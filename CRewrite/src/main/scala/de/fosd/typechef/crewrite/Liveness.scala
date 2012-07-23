package de.fosd.typechef.crewrite

import de.fosd.typechef.parser.c._
import org.kiama.attribution.AttributionBase
import de.fosd.typechef.conditional._
import de.fosd.typechef.featureexpr._

// defines and uses we can jump to using succ
// beware of List[Opt[_]]!! all list elements can possibly have a different annotation
trait Variables {


  // add annotation to elements of a Set[Id]
  // used for uses, defines, and declares
  private def addAnnotation2ResultSet(in: Set[Id], env: ASTEnv): Map[FeatureExpr, Set[Id]] = {
    var res = Map[FeatureExpr, Set[Id]]()

    for (r <- in) {
      val rfexp = env.featureExpr(r)

      val key = res.find(_._1 equivalentTo rfexp)
      key match {
        case None => res = res.+((rfexp, Set(r)))
        case Some((k, v)) => res = res.+((k, v ++ Set(r)))
      }
    }

    res
  }

  // returns all used variables with their annotation
  val usesVar: PartialFunction[(Any, ASTEnv), Map[FeatureExpr, Set[Id]]]= {
    case (a, env) => addAnnotation2ResultSet(uses(a), env)
  }

  // returns all used variables (apart from declarations) with their annotation
  val dataflowUsesVar: PartialFunction[(Any, ASTEnv), Map[FeatureExpr, Set[Id]]]= {
    case (a, env) => addAnnotation2ResultSet(dataflowUses(a), env)
  }

  // returns all defined variables with their annotation
  val definesVar: PartialFunction[(Any, ASTEnv), Map[FeatureExpr, Set[Id]]] = {
    case (a, env) => addAnnotation2ResultSet(defines(a), env)
  }

  // returns all declared variables with their annotation
  val declaresVar: PartialFunction[(Any, ASTEnv), Map[FeatureExpr, Set[Id]]] = {
    case (a, env) => addAnnotation2ResultSet(declares(a), env)
  }

  // returns all used Ids independent of their annotation
  val uses: PartialFunction[Any, Set[Id]] = {
    case ForStatement(expr1, expr2, expr3, _) => uses(expr1) ++ uses(expr2) ++ uses(expr3)
    case ReturnStatement(Some(x)) => uses(x)
    case WhileStatement(expr, _) => uses(expr)
    case DeclarationStatement(d) => uses(d)
    case Declaration(_, init) => init.flatMap(uses).toSet
    case InitDeclaratorI(_, _, Some(i)) => uses(i)
    case AtomicNamedDeclarator(_, id, _) => Set(id)
    case NestedNamedDeclarator(_, nestedDecl, _) => uses(nestedDecl)
    case Initializer(_, expr) => uses(expr)
    case i@Id(name) => Set(i)
    case FunctionCall(params) => params.exprs.map(_.entry).flatMap(uses).toSet
    case ArrayAccess(expr) => uses(expr)
    case PostfixExpr(Id(_), f@FunctionCall(_)) => uses(f)
    case PostfixExpr(p, s) => uses(p) ++ uses(s)
    case UnaryExpr(_, ex) => uses(ex)
    case SizeOfExprU(expr) => uses(expr)
    case CastExpr(_, expr) => uses(expr)
    case PointerDerefExpr(castExpr) => uses(castExpr)
    case PointerCreationExpr(castExpr) => uses(castExpr)
    case UnaryOpExpr(kind, castExpr) => uses(castExpr)
    case NAryExpr(ex, others) => uses(ex) ++ others.flatMap(uses).toSet
    case NArySubExpr(_, ex) => uses(ex)
    case ConditionalExpr(condition, _, _) => uses(condition)
    case ExprStatement(expr) => uses(expr)
    case AssignExpr(target, op, source) => uses(source) ++ ({
      op match {
        case "=" => Set()
        case _ => uses(target)
      }
    })
    case Opt(_, entry) => uses(entry)
    case _ => Set()
  }

  // returns all uses of variables, apart from declarations
  val dataflowUses: PartialFunction[Any, Set[Id]] = {
    case ForStatement(expr1, expr2, expr3, _) => dataflowUses(expr1) ++ dataflowUses(expr2) ++ dataflowUses(expr3)
    case ReturnStatement(Some(x)) => dataflowUses(x)
    case WhileStatement(expr, _) => dataflowUses(expr)
    case DeclarationStatement(d) => dataflowUses(d)
    case Declaration(_, init) => init.flatMap(dataflowUses).toSet
    case InitDeclaratorI(_, _, Some(i)) => dataflowUses(i)
    case AtomicNamedDeclarator(_, id, _) => Set(id)
    case NestedNamedDeclarator(_, nestedDecl, _) => dataflowUses(nestedDecl)
    case Initializer(_, expr) => dataflowUses(expr)
    case i@Id(name) => Set(i)
    case FunctionCall(params) => params.exprs.map(_.entry).flatMap(dataflowUses).toSet
    case ArrayAccess(expr) => dataflowUses(expr)
    case PostfixExpr(Id(_), f@FunctionCall(_)) => dataflowUses(f)
    case PostfixExpr(p, s) => dataflowUses(p) ++ dataflowUses(s)
    case UnaryExpr(_, ex) => dataflowUses(ex)
    case SizeOfExprU(expr) => dataflowUses(expr)
    case CastExpr(_, expr) => dataflowUses(expr)
    case PointerDerefExpr(castExpr) => dataflowUses(castExpr)
    case PointerCreationExpr(castExpr) => dataflowUses(castExpr)
    case UnaryOpExpr(kind, castExpr) => dataflowUses(castExpr)
    case NAryExpr(ex, others) => dataflowUses(ex) ++ others.flatMap(dataflowUses).toSet
    case NArySubExpr(_, ex) => dataflowUses(ex)
    case ConditionalExpr(condition, _, _) => dataflowUses(condition)
    case ExprStatement(expr) => dataflowUses(expr)
    case AssignExpr(target, op, source) => dataflowUses(source) ++ dataflowUses(target)
    case Opt(_, entry) => dataflowUses(entry)
    case _ => Set()
  }

  // returns all defined Ids independent of their annotation
  val defines: PartialFunction[Any, Set[Id]] = {
    case i@Id(_) => Set(i)
    case AssignExpr(target, _, source) => defines(target)
    case DeclarationStatement(d) => defines(d)
    case Declaration(_, init) => init.flatMap(defines).toSet
    case InitDeclaratorI(a, _, _) => defines(a)
    case AtomicNamedDeclarator(_, i, _) => Set(i)
    case ExprStatement(_: Id) => Set()
    case ExprStatement(expr) => defines(expr)
    case PostfixExpr(i@Id(_), SimplePostfixSuffix(_)) => Set(i) // a++; or a--;
    case UnaryExpr(_, i@Id(_)) => Set(i) // ++a; or --a;
    case Opt(_, entry) => defines(entry)
    case _ => Set()
  }

  // returns all declared Ids independent of their annotation
  val declares: PartialFunction[Any, Set[Id]] = {
    case DeclarationStatement(decl) => declares(decl)
    case Declaration(_, init) => init.flatMap(declares).toSet
    case InitDeclaratorI(declarator, _, _) => declares(declarator)
    case AtomicNamedDeclarator(_, id, _) => Set(id)
    case Opt(_, entry) => declares(entry)
    case _ => Set()
  }
}

class LivenessCache {
  private val cache: java.util.IdentityHashMap[Any, Map[FeatureExpr, Set[Id]]] = new java.util.IdentityHashMap[Any, Map[FeatureExpr, Set[Id]]]()

  def update(k: Any, v: Map[FeatureExpr, Set[Id]]) {
    cache.put(k, v)
  }

  def lookup(k: Any): Option[Map[FeatureExpr, Set[Id]]] = {
    val v = cache.get(k)
    if (v != null) Some(v)
    else None
  }
}


trait Liveness extends AttributionBase with Variables with ConditionalControlFlow {

  private val incache = new LivenessCache()
  private val outcache = new LivenessCache()

  private def updateMap(m: Map[FeatureExpr, Set[Id]],
                        e: (FeatureExpr, Set[Id]),
                        diff: Boolean): Map[FeatureExpr, Set[Id]] = {

    if (diff) {
      var cm = Map[FeatureExpr, Set[Id]]()

      for ((k, v) <- m) {
        val ns = v.diff(e._2)
        if (! ns.isEmpty) cm = cm.+((k, ns))
      }
      cm
    } else {
      val key = m.find(_._1.equivalentTo(e._1))

      key match {
        case None => m.+(e)
        case Some((k, v)) => m.+((k, v.union(e._2)))
      }
    }
  }

  // TypeChef does not enforce us to be type-uniform,
  // so a variable use may belong to different variable declarations
  // e.g.:
  // void foo() {
  //   int a = 0; // 3
  //   int b = a;
  //   if (b) {
  //     #if A
  //     int a = b; // 2
  //     #endif
  //     a;  // 1
  //   }
  // }
  // a (// 1) has two different declarations: Choice(A, One(// 3), One(// 2))
  // in presence of A (// 2) shadows declaration (// 3)
  // we compute the relation between variable uses and declarations per function
  def determineUseDeclareRelation(func: FunctionDef, env: ASTEnv): java.util.IdentityHashMap[Id, Option[Conditional[Option[Id]]]] = {
    // we use a working stack to maintain in maintain scoping of nested compound statements
    // each element of the list refers to a block; if we enter a compound statement then we
    // add a Map to the stack; if we leave a compound statement we return the tail of wstack
    // current block is head
    // Map[Id, Conditional[Option[Id]]] maintains all variable declarations in the block that are visible
    type BlockDecls = Map[Id, Conditional[Option[Id]]]
    var res: java.util.IdentityHashMap[Id, Option[Conditional[Option[Id]]]] =
      new java.util.IdentityHashMap[Id, Option[Conditional[Option[Id]]]]()
    var curIdSuffix = 1

    def handleElement(e: Any, curws: List[BlockDecls]): List[BlockDecls] = {
      def handleCFGInstruction(i: AST) = {
        var curblock = curws.head
        val declares = declaresVar(i, env)
        val uses = dataflowUsesVar(i, env)

        // first check uses then update curws using declares
        for ((k, v) <- uses) {
          for (id <- v) {
            val prevblockswithid = curws.flatMap(_.get(id))
            if (prevblockswithid.isEmpty) {
              res.put(id, None)
            } else {
              res.put(id, Some(ConditionalLib.findSubtree(k, prevblockswithid.head)))
            }
          }
        }

        for ((k, v) <- declares) {
          for (id <- v) {
            if (curblock.get(id).isDefined) {
              curblock = curblock.+((id, ConditionalLib.insert[Option[Id]](curblock.get(id).get,
                FeatureExprFactory.True, k, Some(Id(id.name+curIdSuffix.toString)))))
              curIdSuffix += 1
            } else {
              // get previous block with declaring id
              val prevblockswithid = curws.tail.flatMap(_.get(id))
              if (prevblockswithid.isEmpty) {
                curblock = curblock.+((id, Choice(k, One(Some(Id(id.name+curIdSuffix.toString))), One(None))))
                curIdSuffix += 1
              } else {
                curblock = curblock.+((id, Choice(k, One(Some(Id(id.name+curIdSuffix.toString))), prevblockswithid.head).simplify))
                curIdSuffix += 1
              }
            }
          }
        }
        curblock :: curws.tail
      }

      e match {
        // add map to ws when entering a {}; remove when leaving {}
        case CompoundStatement(innerStatements) => handleElement(innerStatements, Map[Id, Conditional[Option[Id]]]()::curws); curws
        case l: List[_] => {
          var newws = curws
          for (s <- l)
            newws = handleElement(s, newws)
          newws
        }

        // statements with special treatment of statements with compound statements in it
        case s: IfStatement => s.productIterator.toList.map(x => handleElement(x, Map[Id, Conditional[Option[Id]]]()::curws)); curws
        case s: ForStatement => s.productIterator.toList.map(x => handleElement(x, Map[Id, Conditional[Option[Id]]]()::curws)); curws
        case s: ElifStatement => s.productIterator.toList.map(x => handleElement(x, Map[Id, Conditional[Option[Id]]]()::curws)); curws
        case s: WhileStatement => s.productIterator.toList.map(x => handleElement(x, Map[Id, Conditional[Option[Id]]]()::curws)); curws
        case s: DoStatement => s.productIterator.toList.map(x => handleElement(x, Map[Id, Conditional[Option[Id]]]()::curws)); curws

        case s: Statement => handleCFGInstruction(s)
        case e: Expr => handleCFGInstruction(e)

        case Opt(_, entry) => handleElement(entry, curws)
        case Choice(_, thenBranch, elseBranch) => handleElement(thenBranch, curws); handleElement(elseBranch, curws)
        case One(value) => handleElement(value, curws)
        case Some(x) => handleElement(x, curws)
        case None => curws

        case _: FeatureExpr => curws
        case x => println("not handling: " + x); curws
      }
    }

    handleElement(func.stmt, List())
    res
  }

  // cache for in; we have to store all tuples of (a, env) their because using
  // (a, env) always creates a new one!!! and circular internally uses another
  // IdentityHashMap and uses (a, env) as a key there.
  private val astIdenEnvHM = new java.util.IdentityHashMap[AST, (AST, ASTEnv)]()
  private implicit def astIdenTup(a: AST) = astIdenEnvHM.get(a)
  type UsesDeclaresRel = java.util.IdentityHashMap[Id, Option[Conditional[Option[Id]]]]

  // cf. http://www.cs.colostate.edu/~mstrout/CS553/slides/lecture03.pdf
  // page 5
  //  in(n) = uses(n) + (out(n) - defines(n))
  // out(n) = for s in succ(n) r = r + in(s); r
  // insimple and outsimple are the non variability-aware in and out versiosn
  // of liveness determination
  val insimple: PartialFunction[(Product, ASTEnv), Set[Id]] = {
    circular[(Product, ASTEnv), Set[Id]](Set()) {
      case t@(FunctionDef(_, _, _, _), _) => Set()
      case t@(e, env) => {
        val u = uses(e)
        val d = defines(e)
        var res = outsimple(t)

        res = u.union(res.diff(d))
        res
      }
    }
  }

  val outsimple: PartialFunction[(Product, ASTEnv), Set[Id]] = {
    circular[(Product, ASTEnv), Set[Id]](Set()) {
      case t@(e, env) => {
        val ss = succ(e, FeatureExprFactory.empty, env).filterNot(x => x.entry.isInstanceOf[FunctionDef])
        var res: Set[Id] = Set()
        for (s <- ss.map(_.entry)) {
          if (!astIdenEnvHM.containsKey(s)) astIdenEnvHM.put(s, (s, env))
          res = res.union(insimple(s))
        }
        res
      }
    }
  }

  private def explodeIdUse(s: Set[Id], sfexp: FeatureExpr, udr: UsesDeclaresRel, res: Map[FeatureExpr, Set[Id]], diff: Boolean) = {
    var curres = res
    for (i <- s) {
      val newname = udr.get(i)
      newname match {
        case null => curres = updateMap(res, (sfexp, Set(i)), diff)
        case None => curres = updateMap(res, (sfexp, Set(i)), diff)
        case Some(c) => {
          val leaves = ConditionalLib.items(c)
          for ((nfexp, nid) <- leaves)
            if (nid.isDefined) curres = updateMap(curres, (sfexp and nfexp, Set(nid.get)), diff)
            else assert(assertion = false, message = "no declaration with new identifier found!")
        }
      }
    }
    curres
  }

  // in and out variability-aware versions
  val inrec: PartialFunction[(Product, FeatureModel, UsesDeclaresRel, ASTEnv), Map[FeatureExpr, Set[Id]]] = {
    circular[(Product, FeatureModel, UsesDeclaresRel, ASTEnv), Map[FeatureExpr, Set[Id]]](Map()) {
      case t@(FunctionDef(_, _, _, _), _, _, _) => Map()
      case t@(e, fm, udr, env) => {
        val uses = usesVar(e, env)
        val defines = definesVar(e, env)

        var res = out(t)
        for ((k, v) <- defines) res = explodeIdUse(v, k, udr, res, diff = true)
        for ((k, v) <- uses)    res = explodeIdUse(v, k, udr, res, diff = false)
        res
      }
    }
  }

  val outrec: PartialFunction[(Product, FeatureModel, UsesDeclaresRel, ASTEnv), Map[FeatureExpr, Set[Id]]] =
    circular[(Product, FeatureModel, UsesDeclaresRel, ASTEnv), Map[FeatureExpr, Set[Id]]](Map()) {
      case t@(e, fm, udr, env) => {
        val ss = succ(e, fm, env).filterNot(x => x.entry.isInstanceOf[FunctionDef])
        var res = Map[FeatureExpr, Set[Id]]()
        for (s <- ss) {
          if (!astIdenEnvHM.containsKey(s)) astIdenEnvHM.put(s.entry, (s.entry, env))
          for ((f, r) <- in((s.entry, fm, udr, env)))
            res = explodeIdUse(r, f, udr, res, diff = false)
        }
        res
      }
    }

  def out(a: (Product, FeatureModel, UsesDeclaresRel, ASTEnv)) = {
    outcache.lookup(a._1) match {
      case Some(v) => v
      case None => {
        val r = outrec(a)
        outcache.update(a._1, r)
        r
      }
    }
  }

  def in(a: (AST, FeatureModel, UsesDeclaresRel, ASTEnv)) = {
    incache.lookup(a._1) match {
      case Some(v) => v
      case None => {
        val r = inrec(a)
        incache.update(a._1, r)
        r
      }
    }
  }
}
