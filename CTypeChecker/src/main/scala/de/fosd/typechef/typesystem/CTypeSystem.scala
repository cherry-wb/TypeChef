package de.fosd.typechef.typesystem

import de.fosd.typechef.parser.c._
import de.fosd.typechef.featureexpr._
import org.kiama.attribution.Attribution._
import org.kiama._
import attribution.Attributable
import de.fosd.typechef.conditional._
import de.fosd.typechef.parser.{WithPosition, Position, NoPosition}

/**
 * checks an AST (from CParser) for type errors (especially dangling references)
 *
 * performs type checking in a single tree-walk, uses lookup functions from various traits
 *
 * @author kaestner
 *
 */
class CTypeSystem(featureModel: FeatureModel = null) extends CTypeAnalysis with FeatureExprLookup with EnforceTreeHelper {

    //    var functionCallChecks = 0

    /*
    * This dictionary groups error messages by function, consolidating duplicate warnings together.
    */
    //    var functionCallErrorMessages: Map[String, ErrorMsgs] = Map()
    //    var functionRedefinitionErrorMessages: List[RedefErrorMsg] = List()

    val startPosition: Attributable ==> Position = attr {
        case a: WithPosition with Attributable =>
            if (a.hasPosition)
                a.getPositionFrom
            else
            if (a.parent == null) NoPosition else a.parent -> startPosition
        case a => if (a.parent == null) NoPosition else a.parent -> startPosition
    }

    trait ErrorMsg

    class SimpleError(msg: String, where: AST) extends ErrorMsg {
        override def toString =
            (where -> startPosition).toString() + ": \n\t" +
                    msg
    }
    class TypeError(msg: String, where: AST, ctype: TConditional[CType]) extends ErrorMsg {
        override def toString =
            (where -> startPosition).toString() + ": \n\t" +
                    msg + "\n" + indentAllLines(prettyPrintType(ctype))
    }

    def prettyPrintType(ctype: TConditional[CType]): String =
        TConditional.toOptList(ctype).map(o => o.feature.toString + ": \t" + o.entry).mkString("\n")

    private def indentAllLines(s: String): String =
        s.lines.map("\t\t" + _).foldLeft("")(_ + "\n" + _)

    var errors: List[ErrorMsg] = List()


    val DEBUG_PRINT = false

    def dbgPrint(o: Any) = if (DEBUG_PRINT) print(o)

    def dbgPrintln(o: Any) = if (DEBUG_PRINT) println(o)

    private val checkNode: Attributable ==> Unit = attr {
        case obj => {
            // Process the errors of the children of t
            for (child <- obj.children)
                child -> checkNode
            checkTree(obj)
            checkAssumptions(obj)
            performCheck(obj)
        }
    }


    def checkAST(ast: TranslationUnit): Boolean = {


        checkNode(prepareAST(ast))

        if (errors.isEmpty)
            println("No type errors found.")
        else {
            println("Found " + errors.size + " type errors: ");
            for (e <- errors)
                println("  - " + e)
        }
        //        println("(performed " + functionCallChecks + " checks regarding function calls)");
        println("\n")
        return errors.isEmpty
    }


    def performCheck(node: Attributable): Unit = node match {
        case fun: FunctionDef => //check function redefinitions
            val priorDefs = fun -> priorDefinitions
            for (priorFun <- priorDefs)
                if (!mex(fun -> featureExpr, priorFun -> featureExpr))
                    issueError("function redefinition of " + fun.getName + " in context " + (fun -> featureExpr) + "; prior definition in context " + (priorFun -> featureExpr), fun, priorFun)

        case expr@PostfixExpr(_, FunctionCall(_)) => // check function calls in PostfixExpressions
            val ct = ctype(expr).simplify(expr -> featureExpr)
            if (ct.exists(_.sometimesUnknown))
                issueTypeError("cannot (always) resolve function call. found type " + ct, expr, ct)

        case ExprStatement(expr) => checkExpr(expr)
        case WhileStatement(expr, _) => checkExpr(expr)
        case DoStatement(expr, _) => checkExpr(expr)
        case ForStatement(expr1, expr2, expr3, _) =>
            if (expr1.isDefined) checkExpr(expr1.get)
            if (expr2.isDefined) checkExpr(expr2.get)
            if (expr3.isDefined) checkExpr(expr3.get)
        //case GotoStatement(expr) => checkExpr(expr) TODO check goto against labels
        case ReturnStatement(expr) => if (expr.isDefined) checkExpr(expr.get)
        case CaseStatement(expr, _) => checkExpr(expr)
        case IfStatement(expr, _, _, _) => checkExpr(expr)
        case ElifStatement(expr, _) => checkExpr(expr)
        case SwitchStatement(expr, _) => checkExpr(expr)

        case _ =>
    }

    private def checkExpr(expr: Expr) = {
        val ct = ctype(expr).simplify(expr -> featureExpr)
        if (ct.exists(_.sometimesUnknown))
            issueTypeError("cannot (always) resolve expression. found type " + ct, expr, ct)
    }

    /**
     * enforce certain assumptions about the layout of the AST
     *
     * these can later be relaxed or automatically ensured by tree transformations
     * before type checking
     */
    def checkAssumptions(node: Attributable): Unit = node match {
        case x: NestedFunctionDef => assert(false, "NestedFunctionDef not supported, yet")
        case _ =>
    }

    /**
     * TODO additional assumptions:
     * * typedef specifier applies to the whole declaration
     *
     */
    private def checkTree(node: Attributable) {
        for (c <- node.children) assert(c.parent == node, "Child " + c + " points to different parent:\n  " + c.parent + "\nshould be\n  " + node)

    }

    private def assertNoVariability[T](l: List[Opt[T]]) {
        def noVariability(o: Opt[T]) =
            (o.feature == FeatureExpr.base) ||
                    (o -> featureExpr implies (o.feature)).isTautology
        assert(l.forall(noVariability), "found unexpected variability in " + l)
    }

    private def mex(a: FeatureExpr, b: FeatureExpr): Boolean = (a mex b).isTautology(featureModel)

    private def issueError(msg: String, where: AST, whereElse: AST = null) {
        errors = new SimpleError(msg, where) :: errors
    }

    private def issueTypeError(msg: String, where: AST, ctype: TConditional[CType]) {
        errors = new TypeError(msg, where, ctype) :: errors
    }

    //
    //    def checkFunctionCallTargets(source: AST, name: String, callerFeature: FeatureExpr, targets: List[Entry]) = {
    //        if (!targets.isEmpty) {
    //            //condition: feature implies (target1 or target2 ...)
    //            functionCallChecks += 1
    //            val condition = callerFeature.implies(targets.map(_.feature).foldLeft(FeatureExpr.base.not)(_.or(_)))
    //            if (condition.isTautology(null) || condition.isTautology(featureModel)) {
    //                dbgPrintln(" always reachable " + condition)
    //                None
    //            } else {
    //                dbgPrintln(" not always reachable " + callerFeature + " => " + targets.map(_.feature).mkString(" || "))
    //                Some(functionCallErrorMessages.get(name) match {
    //                    case None => ErrorMsgs(name, List((callerFeature, source)), targets)
    //                    case Some(err: ErrorMsgs) => err.withNewCaller(source, callerFeature)
    //                })
    //            }
    //        } else {
    //            dbgPrintln("dead")
    //            Some(ErrorMsgs.errNoDecl(name, source, callerFeature))
    //        }
    //    }

    //
    //
    //    def checkFunctionRedefinition(env: LookupTable) {
    //        val definitions = env.byNames
    //        for ((name, defs) <- definitions) {
    //            if (defs.size > 1) {
    //                var fexpr = defs.head.feature
    //                for (adef <- defs.tail) {
    //                    if (!(adef.feature mex fexpr).isTautology(featureModel)) {
    //                        dbgPrintln("function " + name + " redefined with feature " + adef.feature + "; previous: " + fexpr)
    //                        functionRedefinitionErrorMessages = RedefErrorMsg(name, adef, fexpr) :: functionRedefinitionErrorMessages
    //                    }
    //                    fexpr = fexpr or adef.feature
    //                }
    //            }
    //        }
    //    }
    //
    //    val checkFunctionCalls: Attributable ==> Unit = attr {
    //        case obj => {
    //            // Process the errors of the children of t
    //            for (child <- obj.children)
    //                checkFunctionCalls(child)
    //            obj match {
    //            //function call (XXX: PG: not-so-good detection, but will work for typical code).
    //                case e@PostfixExpr(Id(name), FunctionCall(_)) => {
    //                    //Omit feat2, for typical code a function call is always a function call, even if the parameter list is conditional.
    //                    checkFunctionCall(e -> env, e, name, e -> presenceCondition)
    //                }
    //                case _ =>
    //            }
    //        }
    //    }
    //
    //
    //    def checkFunctionCall(table: LookupTable, source: AST, name: String, callerFeature: FeatureExpr) {
    //        val targets: List[Entry] = table.find(name)
    //        dbgPrint("function " + name + " found " + targets.size + " targets: ")
    //        checkFunctionCallTargets(source, name, callerFeature, targets) match {
    //            case Some(newEntry) =>
    //                functionCallErrorMessages = functionCallErrorMessages.updated(name, newEntry)
    //            case _ => ()
    //        }
    //    }

}

