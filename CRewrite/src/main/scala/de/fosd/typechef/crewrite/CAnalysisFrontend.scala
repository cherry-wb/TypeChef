
package de.fosd.typechef.crewrite

import scala.Some
import java.io.{Writer, StringWriter}

import de.fosd.typechef.featureexpr._
import de.fosd.typechef.typesystem._
import de.fosd.typechef.parser.c._
import de.fosd.typechef.error._

class CAnalysisFrontend(tu: TranslationUnit, fm: FeatureModel = FeatureExprFactory.empty) extends CFGHelper with EnforceTreeHelper {

    val tunit = prepareAST[TranslationUnit](tu)

    def dumpCFG(writer: Writer = new StringWriter()) {
        val fdefs = filterAllASTElems[FunctionDef](tunit)
        val dump = new DotGraph(writer)
        val env = CASTEnv.createASTEnv(tunit)
        dump.writeHeader("CFGDump")

        for (f <- fdefs) {
            dump.writeMethodGraph(getAllSucc(f, fm, env), env, Map())
        }
        dump.writeFooter()
        dump.close()

        if (writer.isInstanceOf[StringWriter])
            println(writer.toString)
    }

    def doubleFree() = {

        val casestudy = {
            tunit.getFile match {
                case None => ""
                case Some(x) => {
                    if (x.contains("linux")) "linux"
                    else if (x.contains("openssl")) "openssl"
                    else ""
                }
            }
        }

        val ts = new CTypeSystemFrontend(tunit, fm) with CDeclUse
        assert(ts.checkASTSilent, "typecheck fails!")
        val env = CASTEnv.createASTEnv(tunit)
        val udm = ts.getUseDeclMap

        val fdefs = filterAllASTElems[FunctionDef](tunit)
        val errors = fdefs.flatMap(doubleFreeFunctionDef(_, env, udm, casestudy))

        if (errors.isEmpty) {
            println("No double frees found!")
        } else {
            println(errors.map(_.toString + "\n").reduce(_ + _))
        }

        errors.isEmpty
    }

    private def doubleFreeFunctionDef(f: FunctionDef, env: ASTEnv, udm: UseDeclMap, casestudy: String): List[TypeChefError] = {
        var res: List[TypeChefError] = List()

        // It's ok to use FeatureExprFactory.empty here.
        // Using the project's fm is too expensive since control
        // flow computation requires a lot of sat calls.
        // We use the proper fm in DoubleFree (see MonotoneFM).
        val ss = getAllSucc(f, FeatureExprFactory.empty, env).reverse
        val df = new DoubleFree(env, udm, fm, casestudy)

        val nss = ss.map(_._1).filterNot(x => x.isInstanceOf[FunctionDef])

        for (s <- nss) {
            val g = df.gen(s)
            val out = df.out(s)

            for ((i, h) <- out)
                for ((f, j) <- g)
                    j.find(_ == i) match {
                        case None =>
                        case Some(x) => {
                            val xdecls = udm.get(x)
                            var idecls = udm.get(i)
                            if (idecls == null)
                                idecls = List(i)
                            for (ei <- idecls)
                                if (xdecls.exists(_.eq(ei)))
                                    res ::= new TypeChefError(Severity.Warning, h, "warning: Variable " + x.name + " is freed multiple times!", x, "")
                        }
                    }
        }

        res
    }

    def uninitializedMemory(): Boolean = {
        val ts = new CTypeSystemFrontend(tunit, fm) with CDeclUse
        assert(ts.checkAST(), "typecheck fails!")
        val env = CASTEnv.createASTEnv(tunit)
        val udm = ts.getUseDeclMap

        val fdefs = filterAllASTElems[FunctionDef](tunit)
        val errors = fdefs.flatMap(uninitializedMemory(_, env, udm))

        if (errors.isEmpty) {
            println("No uages of uninitialized memory found!")
        } else {
            println(errors.map(_.toString + "\n").reduce(_ + _))
        }

        errors.isEmpty
    }

    private def uninitializedMemory(f: FunctionDef, env: ASTEnv, udm: UseDeclMap): List[TypeChefError] = {
        var res: List[TypeChefError] = List()

        // It's ok to use FeatureExprFactory.empty here.
        // Using the project's fm is too expensive since control
        // flow computation requires a lot of sat calls.
        // We use the proper fm in UninitializedMemory (see MonotoneFM).
        val ss = getAllPred(f, FeatureExprFactory.empty, env).reverse
        val um = new UninitializedMemory(env, udm, fm)
        val nss = ss.map(_._1).filterNot(x => x.isInstanceOf[FunctionDef])

        for (s <- nss) {
            val g = um.getFunctionCallArguments(s)
            val in = um.in(s)

            for ((i, h) <- in)
                for ((f, j) <- g)
                    j.find(_ == i) match {
                        case None =>
                        case Some(x) => {
                            val xdecls = udm.get(x)
                            var idecls = udm.get(i)
                            if (idecls == null)
                                idecls = List(i)
                            for (ei <- idecls)
                                if (xdecls.exists(_.eq(ei)))
                                    res ::= new TypeChefError(Severity.Warning, h, "warning: Variable " + x.name + " is used uninitialized!", x, "")
                        }
                    }
        }

        res
    }

    def xfree(): Boolean = {
        val ts = new CTypeSystemFrontend(tunit, fm) with CDeclUse
        assert(ts.checkAST(), "typecheck fails!")
        val env = CASTEnv.createASTEnv(tunit)
        val udm = ts.getUseDeclMap

        val fdefs = filterAllASTElems[FunctionDef](tunit)
        val errors = fdefs.flatMap(xfree(_, env, udm))

        if (errors.isEmpty) {
            println("No uages of uninitialized memory found!")
        } else {
            println(errors.map(_.toString + "\n").reduce(_ + _))
        }

        errors.isEmpty
    }

    private def xfree(f: FunctionDef, env: ASTEnv, udm: UseDeclMap): List[TypeChefError] = {
        var res: List[TypeChefError] = List()

        // It's ok to use FeatureExprFactory.empty here.
        // Using the project's fm is too expensive since control
        // flow computation requires a lot of sat calls.
        // We use the proper fm in UninitializedMemory (see MonotoneFM).
        val ss = getAllPred(f, FeatureExprFactory.empty, env).reverse
        val xf = new XFree(env, udm, fm, "")
        val nss = ss.map(_._1).filterNot(x => x.isInstanceOf[FunctionDef])

        for (s <- nss) {
            val g = xf.freedVariables(s)
            val in = xf.in(s)

            for ((i, h) <- in)
                for ((f, j) <- g)
                    j.find(_ == i) match {
                        case None =>
                        case Some(x) => {
                            val xdecls = udm.get(x)
                            var idecls = udm.get(i)
                            if (idecls == null)
                                idecls = List(i)
                            for (ei <- idecls)
                                if (xdecls.exists(_.eq(ei)))
                                    res ::= new TypeChefError(Severity.Warning, h, "warning: Variable " + x.name + " is freed although not dynamically allocted!", x, "")
                        }
                    }
        }

        res
    }

    def danglingSwitchCode(): Boolean = {
        val ts = new CTypeSystemFrontend(tunit, fm) with CDeclUse
        assert(ts.checkASTSilent, "typecheck fails!")
        val env = CASTEnv.createASTEnv(tunit)

        val fdefs = filterAllASTElems[FunctionDef](tunit)
        val errors = fdefs.flatMap(danglingSwitchCode(_, env))

        if (errors.isEmpty) {
            println("No dangling code in switch statements found!")
        } else {
            println(errors.map(_.toString + "\n").reduce(_ + _))
        }

        !errors.isEmpty
    }

    private def danglingSwitchCode(f: FunctionDef, env: ASTEnv): List[TypeChefError] = {
        val ss = filterAllASTElems[SwitchStatement](f)
        val ds = new DanglingSwitchCode(env, FeatureExprFactory.empty)

        ss.flatMap(s => {
            ds.danglingSwitchCode(s).map(e => {
                new TypeChefError(Severity.Warning, e.feature, "warning: switch statement has dangling code ", e.entry, "")
            })

        })
    }

    def cfgNonVoidFunction(): Boolean = {
        val ts = new CTypeSystemFrontend(tunit, fm) with CTypeCache
        assert(ts.checkASTSilent, "typecheck fails!")
        val env = CASTEnv.createASTEnv(tunit)

        val fdefs = filterAllASTElems[FunctionDef](tunit)
        val errors = fdefs.flatMap(cfgNonVoidFunction(_, env, ts))

        if (errors.isEmpty) {
            println("Control flow in non-void functions always ends in return statements!")
        } else {
            println(errors.map(_.toString + "\n").reduce(_ + _))
        }

        errors.isEmpty
    }

    private def cfgNonVoidFunction(f: FunctionDef, env: ASTEnv, ts: CTypeSystemFrontend with CTypeCache): List[TypeChefError] = {
        val cf = new CFGNonVoidFunction(env, fm, ts)

        cf.cfgReturn(f).map(
            e => new TypeChefError(Severity.Warning, e.feature, "Control flow of non-void function ends here!", e.entry, "")
        )
    }

    def checkStdLibFuncReturn(): Boolean = {
        val ts = new CTypeSystemFrontend(tunit, fm)
        assert(ts.checkAST(), "typecheck fails!")
        val env = CASTEnv.createASTEnv(tunit)

        val fdefs = filterAllASTElems[FunctionDef](tunit)
        val errors = fdefs.flatMap(checkStdLibFuncReturn(_, env, ts.getUseDeclMap))

        if (errors.isEmpty) {
            println("Return values of stdlib functions are properly checked for errors!")
        } else {
            println(errors.map(_.toString + "\n").reduce(_ + _))
        }

        errors.isEmpty
    }

    private def checkStdLibFuncReturn(f: FunctionDef, env: ASTEnv, udm: UseDeclMap): List[TypeChefError] = {
        var errors: List[TypeChefError] = List()
        val ss = getAllSucc(f, FeatureExprFactory.empty, env).map(_._1).filterNot(_.isInstanceOf[FunctionDef])
        val cl: List[CheckStdLibFuncReturn] = List(
            //new CheckStdLibFuncReturn_EOF(env, udm, fm),
            new CheckStdLibFuncReturn_Null(env, udm, fm)
        )

        for (s <- ss) {
            for (cle <- cl) {
                lazy val errorvalues = cle.errorreturn.map(PrettyPrinter.print(_)).mkString(" 'or' ")

                // check CFG element directly; without dataflow analysis
                for (e <- cle.checkForPotentialCalls(s)) {
                    errors ::= new TypeChefError(Severity.SecurityWarning, env.featureExpr(e), "Return value of " +
                        PrettyPrinter.print(e) + " is not properly checked for (" + errorvalues + ")!", e)
                }

                // stdlib call is assigned to a variable that we track with our dataflow analysis
                // we check whether used variables that hold the value of a stdlib function are killed in s,
                // if not we report an error
                for ((e, fi) <- cle.out(s)) {
                    for ((fu, u) <- cle.getUsedVariables(s)) {
                        u.find(_ == e) match {
                            case None =>
                            case Some(x) => {
                                val xdecls = udm.get(x)
                                var edecls = udm.get(e)
                                if (edecls == null) edecls = List(e)

                                for (ee <- edecls) {
                                    val kills = cle.kill(s)
                                    if (xdecls.exists(_.eq(ee)) && (!kills.contains(fu) || kills.contains(fu) && !kills(fu).contains(x))) {
                                        errors ::= new TypeChefError(Severity.SecurityWarning, fi, "The value of " +
                                            PrettyPrinter.print(e) + " is not properly checked for (" + errorvalues + ")!", e)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        errors
    }
}
