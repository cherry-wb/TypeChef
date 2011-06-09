package de.fosd.typechef.typesystem


import de.fosd.typechef.parser.c._
import de.fosd.typechef.parser._
import de.fosd.typechef.featureexpr.FeatureExpr
import org.kiama.attribution.Attribution._
import org.kiama._

/**
 * parsing types from declarations (top level declarations, parameters, etc)
 *
 * handling of typedef synonyms
 */
trait CDeclTyping extends CTypes with ASTNavigation with FeatureExprLookup {

    def ctype(fun: FunctionDef) = fun -> funType
    val funType: FunctionDef ==> CType = attr {
        case fun =>
            if (!fun.oldStyleParameters.isEmpty) CUnknown("alternative parameter notation not supported yet")
            else if (isTypedef(fun.specifiers)) CUnknown("Invalid typedef specificer for function definition (?)")
            else declType(fun.specifiers, fun.declarator)
    }


    /**
     * filtering is a workaround for a parsing problem (see open test) that can produce
     * dead AST-subtrees in some combinations.
     *
     * remove when problem is fixed
     */
    protected def filterDeadSpecifiers[T](l: List[Opt[T]], ctx: FeatureExpr): List[Opt[T]] =
        l.filter(o => ((o.feature) and ctx).isSatisfiable)


    def constructType(specifiers: List[Opt[Specifier]]): CType = {

        //checked assumption: there is no variability in specifier lists
        assert(specifiers.forall(o => ((o -> featureExpr) implies (o.feature)).isTautology), "Unexpected variability in specifiers: " + specifiers + "  " + featureExpr(specifiers.head))


        //type specifiers
        var types = List[CType]()
        for (Opt(_, specifier) <- specifiers) specifier match {
            case StructOrUnionSpecifier(isUnion, Some(id), _) => types = types :+ CStruct(id.name, isUnion)
            case StructOrUnionSpecifier(isUnion, None, members) => types = types :+ CAnonymousStruct(parseStructMembers(members).map(x => (x._1, x._3)), isUnion)
            case e@TypeDefTypeSpecifier(Id(typedefname)) => {
                val typedefEnvironment = e -> previousTypedefEnv
                if (typedefEnvironment contains typedefname) types = types :+ typedefEnvironment(typedefname)
                else types = types :+ CUnknown("type not defined " + typedefname) //should not occur, because the parser should reject this already
            }
            case EnumSpecifier(_, _) => types = types :+ CSigned(CInt()) //TODO check that enum name is actually defined (not urgent, there is not much checking possible for enums anyway)
            case _ =>
        }



        def count(spec: Specifier): Int = specifiers.count({
            case Opt(_, s) => spec == s
            case _ => false
        })
        def has(spec: Specifier): Boolean = count(spec) > 0

        val isSigned = has(SignedSpecifier())
        val isUnsigned = has(UnsignedSpecifier())
        if (isSigned && isUnsigned)
            return CUnknown("type both signed and unsigned")

        //for int, short, etc, always assume signed by default
        def sign(t: CBasicType): CType = if (isUnsigned) CUnsigned(t) else CSigned(t)
        //for characters care about SignUnspecified
        def signC(t: CBasicType): CType = if (isSigned) CSigned(t) else if (isUnsigned) CUnsigned(t) else CSignUnspecified(t)

        if (has(CharSpecifier()))
            types = types :+ signC(CChar())
        if (count(LongSpecifier()) == 2)
            types = types :+ sign(CLongLong())
        if (count(LongSpecifier()) == 1 && !has(DoubleSpecifier()))
            types = types :+ sign(CLong())
        if (has(ShortSpecifier()))
            types = types :+ sign(CShort())
        if (has(DoubleSpecifier()) && has(LongSpecifier()))
            types = types :+ CLongDouble()
        if (has(DoubleSpecifier()) && !has(LongSpecifier()))
            types = types :+ CDouble()
        if (has(FloatSpecifier()))
            types = types :+ CFloat()
        if ((isSigned || isUnsigned || has(IntSpecifier())) && !has(ShortSpecifier()) && !has(LongSpecifier()) && !has(CharSpecifier()))
            types = types :+ sign(CInt())

        if (has(VoidSpecifier()))
            types = types :+ CVoid()

        //TODO prevent invalid combinations completely?


        if (types.size == 1)
            types.head
        else if (types.size == 0)
            CUnknown("no type specfier found")
        else
            CUnknown("multiple types found " + types)
        //
        //                      | textToken("char")
        //            | textToken("short")
        //            | textToken("int")
        //            | textToken("long")
        //            | textToken("float")
        //            | textToken("double")
        //            | signed
        //            | textToken("unsigned")
        //            | textToken("_Bool")
        //            | textToken("_Complex")
        //            | textToken("__complex__")) ^^ {(t: Elem) => PrimitiveTypeSpecifier(t.getText)})
        //            | structOrUnionSpecifier
        //            | enumSpecifier
        //            //TypeDefName handled elsewhere!
        //            | (typeof ~ LPAREN ~> ((typeName ^^ {TypeOfSpecifierT(_)})
        //            | (expr ^^ {TypeOfSpecifierU(_)})) <~ RPAREN))

    }

    def declType(decl: Declaration): List[(String, FeatureExpr, CType)] = enumDeclarations(decl.declSpecs) ++ {
        if (isTypedef(decl.declSpecs)) List() //no declaration for a typedef
        else {
            val returnType = constructType(filterDeadSpecifiers(decl.declSpecs, decl -> featureExpr))

            for (Opt(f, init) <- decl.init) yield (init.declarator.getName, init -> featureExpr, getDeclaratorType(init.declarator, returnType))
        }
    }

    /**define all fields from enum type specifiers as int values */
    private def enumDeclarations(specs: List[Opt[Specifier]]): List[(String, FeatureExpr, CType)] = {
        var result = List[(String, FeatureExpr, CType)]()
        for (Opt(_, spec) <- specs) spec match {
            case EnumSpecifier(_, Some(enums)) => for (Opt(_, enum) <- enums)
                result = (enum.id.name, enum -> featureExpr, CSigned(CInt())) :: result
            case _ =>
        }
        result
    }


    def declType(specs: List[Opt[Specifier]], decl: Declarator) =
        getDeclaratorType(decl, constructType(specs))

    def isTypedef(specs: List[Opt[Specifier]]) = specs.map(_.entry).contains(TypedefSpecifier())

    protected def getDeclaratorType(decl: Declarator, returnType: CType): CType = {
        val rtype = decorateDeclaratorExt(decorateDeclaratorPointer(returnType, decl.pointers), decl.extensions)

        //this is an absurd order but seems to be as specified
        //cf. http://www.ericgiguere.com/articles/reading-c-declarations.html
        decl match {
            case AtomicNamedDeclarator(ptrList, name, e) => rtype
            case NestedNamedDeclarator(ptrList, innerDecl, e) => getDeclaratorType(innerDecl, rtype)
        }
    }

    private def getAbstractDeclaratorType(decl: AbstractDeclarator, returnType: CType): CType = {
        val rtype = decorateDeclaratorExt(decorateDeclaratorPointer(returnType, decl.pointers), decl.extensions)

        //this is an absurd order but seems to be as specified
        //cf. http://www.ericgiguere.com/articles/reading-c-declarations.html
        decl match {
            case AtomicAbstractDeclarator(ptrList, e) => rtype
            case NestedAbstractDeclarator(ptrList, innerDecl, e) => getAbstractDeclaratorType(innerDecl, rtype)
        }
    }

    private def decorateDeclaratorExt(t: CType, extensions: List[Opt[DeclaratorExtension]]): CType = {
        var rtype = t
        for (Opt(_, ext) <- extensions.reverse) rtype = ext match {
            case DeclIdentifierList(idList) => if (idList.isEmpty) CFunction(Seq(), rtype) else CUnknown("cannot derive type of function in this style yet")
            case DeclParameterDeclList(parameterDecls) => CFunction(getParameterTypes(parameterDecls), rtype)
            case DeclArrayAccess(expr) => CArray(rtype)
        }
        rtype
    }
    private def decorateDeclaratorPointer(t: CType, pointers: List[Opt[Pointer]]): CType = pointers.foldRight(t)((a, b) => CPointer(b))


    private def getParameterTypes(parameterDecls: List[Opt[ParameterDeclaration]]) = {
        for (Opt(_, param) <- parameterDecls) yield param match {
            case PlainParameterDeclaration(specifiers) => constructType(specifiers)
            case ParameterDeclarationD(specifiers, decl) => getDeclaratorType(decl, constructType(specifiers))
            case ParameterDeclarationAD(specifiers, decl) => getAbstractDeclaratorType(decl, constructType(specifiers))
            case VarArgs() => CVarArgs()
        }

    }

    def parseStructMembers(members: List[Opt[StructDeclaration]]): List[(String, FeatureExpr, CType)] = {
        var result = List[(String, FeatureExpr, CType)]()
        for (Opt(f, attr) <- members; Opt(g, strDecl) <- attr.declaratorList) strDecl match {
            case StructDeclarator(decl, _, _) => result = result :+ ((decl.getName, f and g, declType(attr.qualifierList, decl)))
            case StructInitializer(expr, _) => //TODO check: ignored for now, does not have a name, seems not addressable. occurs for example in struct timex in async.i test
        }
        result
    }


    /*************
     * Typedef environment (all defined type synonyms up to here)
     */
    //Type synonyms with typedef
    type TypeDefEnv = Map[String, CType]

    /**typedef enviroment, outside the current declaration*/
    val previousTypedefEnv: AST ==> TypeDefEnv = {
        case ast: AST =>
            if (!(ast -> inDeclaration)) ast -> typedefEnv
            else (ast -> outerDeclaration -> prevOrParentAST -> typedefEnv)
    }

    val typedefEnv: AST ==> TypeDefEnv = attr {
        //TODO variability
        case e: Declaration => outerTypedefEnv(e) ++ recognizeTypedefs(e)
        case e: AST => outerTypedefEnv(e)
    }
    private def outerTypedefEnv(e: AST): TypeDefEnv =
        outer[TypeDefEnv](typedefEnv, () => Map(), e)

    private def recognizeTypedefs(decl: Declaration): TypeDefEnv = {
        if (isTypedef(decl.declSpecs))
            (for (Opt(f, init) <- decl.init) yield (init.getName -> declType(decl.declSpecs, init.declarator))) toMap
        else Map()
    }

    private val inDeclaration: AST ==> Boolean = attr {
        case e: Declaration => true
        case e: AST => if (e -> parentAST == null) false else e -> parentAST -> inDeclaration
    }
    //get the first parent node that is a declaration
    private val outerDeclaration: AST ==> Declaration = attr {
        case e: Declaration => e
        case e: AST => if ((e -> parentAST) == null) null else e -> parentAST -> outerDeclaration
    }

}