package de.fosd.typechef.crewrite

import org.junit.Test
import org.scalatest.matchers.ShouldMatchers
import de.fosd.typechef.featureexpr.FeatureExprFactory
import de.fosd.typechef.parser.c._
import de.fosd.typechef.typesystem.{CDeclUse, CTypeSystemFrontend}

class UninitializedMemoryTest extends TestHelper with ShouldMatchers with CFGHelper with EnforceTreeHelper {

    private def getKilledVariables(code: String) = {
        val a = parseCompoundStmt(code)
        val um = new UninitializedMemory(CASTEnv.createASTEnv(a), null, null)
        um.kill(a)
    }

    private def getGeneratedVariables(code: String) = {
        val a = parseCompoundStmt(code)
        val um = new UninitializedMemory(CASTEnv.createASTEnv(a), null, null)
        um.gen(a)
    }

    private def getFunctionCallArguments(code: String) = {
        val a = parseExpr(code)
        val um = new UninitializedMemory(CASTEnv.createASTEnv(a), null, null)
        um.getFunctionCallArguments(a)
    }

    def uninitializedMemory(code: String): Boolean = {
        val tunit = prepareAST[TranslationUnit](parseTranslationUnit(code))
        val um = new CIntraAnalysisFrontend(tunit)
        val ts= new CTypeSystemFrontend(tunit) with CDeclUse
        assert(ts.checkASTSilent, "typecheck fails!")
        um.uninitializedMemory()
    }

    @Test def test_variables() {
        getKilledVariables("{ int a; }") should be(Map())
        getKilledVariables("{ int a = 2; }") should be(Map(Id("a") -> FeatureExprFactory.True))
        getKilledVariables("{ int a, b = 1; }") should be(Map(Id("b") -> FeatureExprFactory.True))
        getKilledVariables("{ int a = 1, b; }") should be(Map(Id("a") -> FeatureExprFactory.True))
        getKilledVariables("{ int *a; }") should be(Map())
        getKilledVariables("{ a = 2; }") should be(Map(Id("a") -> FeatureExprFactory.True))
        getKilledVariables("{ int a[5]; }") should be(Map())
        getKilledVariables("""{
              #ifdef A
              int a;
              #endif
              }""".stripMargin) should be(Map())
        getGeneratedVariables("{ int a; }") should be(Map(Id("a") -> FeatureExprFactory.True))
        getGeneratedVariables("{ int a = 2; }") should be(Map())
        getGeneratedVariables("{ int a, b = 1; }") should be(Map(Id("a") -> FeatureExprFactory.True))
        getGeneratedVariables("{ int a = 1, b; }") should be(Map(Id("b") -> FeatureExprFactory.True))
        getGeneratedVariables("{ int *a; }") should be(Map(Id("a") -> FeatureExprFactory.True))
        getGeneratedVariables("{ a = 2; }") should be(Map())
        getGeneratedVariables("{ int a[5]; }") should be(Map(Id("a") -> FeatureExprFactory.True))
        getGeneratedVariables("""{
              #ifdef A
              int a;
              #endif
              }""".stripMargin) should be(Map(Id("a") -> fa))
    }

    @Test def test_functioncall_arguments() {
        getFunctionCallArguments("foo(a,b)") should be(Map(Id("a") -> FeatureExprFactory.True, Id("b") -> FeatureExprFactory.True))
        getFunctionCallArguments("foo(a,bar(c))") should be(Map(Id("a") -> FeatureExprFactory.True, Id("c") -> FeatureExprFactory.True))
    }

    @Test def test_uninitialized_memory_simple() {
        uninitializedMemory( """
        void get_sign(int number, int *sign) {
            if (sign == 0) {
                 /* ... */
            }
            if (number > 0) {
                *sign = 1;
            } else if (number < 0) {
                *sign = -1;
            } // If number == 0, sign is not changed.
        }
        int is_negative(int number) {
            int sign;
            get_sign(number, &sign);
            return (sign < 0); // diagnostic required
        }""".stripMargin) should be(false)

        uninitializedMemory( """
        int do_auth() { return 0; }
        int printf(const char *format, ...);
        int sprintf(char *str, const char* format, ...) { return 0; }
        void report_error(const char *msg) {
            const char *error_log;
            char buffer[24];
            sprintf(buffer, "Error: %s", error_log); // diagnostic required
            printf("%s\n", buffer);
        }
        int main(void) {
            if (do_auth() == -1) {
                report_error("Unable to login");
            }
            return 0;
        }""".stripMargin) should be(false)

        uninitializedMemory( """
        void close(int i) { }
        void foo() {
            int fd;
            close(fd);
        }""".stripMargin) should be(false)

        uninitializedMemory( """
        void close(int i) { }
        void foo() {
            int fd;
            fd = 2;
            close(fd);
        }""".stripMargin) should be(true)

        uninitializedMemory( """
        void close(int i) { }
        void foo() {
            int fd;
            #ifdef A
            fd = 2;
            #endif
            close(fd);
        }""".stripMargin) should be(false)
    }
}
