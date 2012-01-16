package de.fosd.typechef.crewrite

import org.scalatest.matchers.ShouldMatchers
import de.fosd.typechef.parser.c._
import de.fosd.typechef.conditional.{Opt, One}
import de.fosd.typechef.featureexpr.{FeatureExpr, True}
import org.junit.{Ignore, Test}

class ConditionalControlFlowGraphTest extends TestHelper with ShouldMatchers with ConditionalControlFlow with LivenessImpl with VariablesImpl with CASTEnv {

  @Test def test_if_the_else() {
    val a = parseCompoundStmt("""
    {
      #ifdef A
      int a;
      #elif defined(B)
      int b;
      #else
      int c;
      #endif
    }
    """)

    val env = createASTEnv(a)
    DotGraph.map2file(getAllSucc(a, env), env.asInstanceOf[DotGraph.ASTEnv])
  }

  @Test def test_simple_ifdef() {
    val a = parseCompoundStmt("""
    {
      int a0;
      #ifdef A1
      int a1;
      #endif
      int a2;
    }
    """)

    val env = createASTEnv(a)
    DotGraph.map2file(getAllSucc(a, env), env.asInstanceOf[DotGraph.ASTEnv])
  }

  @Test def test_for_loop() {
    val a = parseCompoundStmt("""
    {
      for (;;) { }
    }
    """)

    val env = createASTEnv(a)
    DotGraph.map2file(getAllSucc(a, env), env.asInstanceOf[DotGraph.ASTEnv])
  }

  @Test def test_nested_loop() {
    val a = parseCompoundStmt("""
    {
      for(;;) {
        for(;;) {
          for(;;) {
          }
        }
      }
    }
    """)

    val env = createASTEnv(a)
    DotGraph.map2file(getAllSucc(a, env), env.asInstanceOf[DotGraph.ASTEnv])
  }

  @Test def test_switch_case() {
    val a = parseCompoundStmt("""
    {
      switch(x) {
      case 1: break;
      case 2: break;
      case 3: break;
      default: break;
      }
    }
    """)

    val env = createASTEnv(a)
    DotGraph.map2file(getAllSucc(a, env), env.asInstanceOf[DotGraph.ASTEnv])
  }

  @Test def test_do_while_loop() {
    val a = parseCompoundStmt("""
    {
      do {
      } while (k);
    }
    """)

    val env = createASTEnv(a)
    DotGraph.map2file(getAllSucc(a, env), env.asInstanceOf[DotGraph.ASTEnv])
  }

  @Test def test_while_loop() {
    val a = parseCompoundStmt("""
    {
      while (k) {
        k--;
      }
    }
    """)

    val env = createASTEnv(a)
    DotGraph.map2file(getAllSucc(a, env), env.asInstanceOf[DotGraph.ASTEnv])
  }

  @Test def test_if_the_else_chain() {
    val a = parseCompoundStmt("""
    {
      int k = 3;
      if (k < 3) {
        k = -1;
      }
      #ifdef A
      else if (k = 3) {
        k = 0;
      }
      #endif
      else {
        k = 1;
      }
    }
    """)

    val env = createASTEnv(a)
    DotGraph.map2file(getAllSucc(a, env), env.asInstanceOf[DotGraph.ASTEnv])
  }

  @Test def test_conditional_labelstatements_if_elif_else() {
    val e1 = Opt(True, LabelStatement(Id("e1"), None))
    val e2 = Opt(fx, LabelStatement(Id("e2"), None))
    val e3 = Opt(fy.and(fx.not()), LabelStatement(Id("e3"), None))
    val e4 = Opt(fy.not().and(fx.not()), LabelStatement(Id("e4"), None))
    val e5 = Opt(True, LabelStatement(Id("e5"), None))
    val c = One(CompoundStatement(List(e1, e2, e3, e4, e5)))

    val env = createASTEnv(c.value)
    succ(e1, env) should be (List(e2.entry, e3.entry, e4.entry))
    succ(e2, env) should be (List(e5.entry))
    succ(e3, env) should be (List(e5.entry))
    succ(e4, env) should be (List(e5.entry))
    DotGraph.map2file(getAllSucc(e1.entry, env), env.asInstanceOf[DotGraph.ASTEnv])
  }

  @Test def test_conditional_labelstatements_with_sequence_of_annotated_elements() {
    val e1 = Opt(True, LabelStatement(Id("e1"), None))
    val e2 = Opt(fx, LabelStatement(Id("e2"), None))
    val e3 = Opt(fx, LabelStatement(Id("e3"), None))
    val e4 = Opt(fx.not(), LabelStatement(Id("e4"), None))
    val e5 = Opt(True, LabelStatement(Id("e5"), None))
    val c = One(CompoundStatement(List(e1, e2, e3, e4, e5)))

    val env = createASTEnv(c.value)
    succ(e1, env) should be(List(e2.entry, e4.entry))
    succ(e2, env) should be(List(e3.entry))
    succ(e3, env) should be(List(e5.entry))
    succ(e4, env) should be(List(e5.entry))
    DotGraph.map2file(getAllSucc(e1.entry, env), env.asInstanceOf[DotGraph.ASTEnv])
  }

  @Test def test_conditional_labelstatements_if_if_else() {
    val e0 = Opt(fx, LabelStatement(Id("e0"), None))
    val e1 = Opt(True, LabelStatement(Id("e1"), None))
    val e2 = Opt(True, LabelStatement(Id("e2"), None))
    val e3 = Opt(fx, LabelStatement(Id("e3"), None))
    val e4 = Opt(fx.not().and(fy), LabelStatement(Id("e4"), None))
    val e5 = Opt(fx.not().and(fy.not()), LabelStatement(Id("e5"), None))
    val e6 = Opt(fa, LabelStatement(Id("e6"), None))
    val e7 = Opt(fa.not(), LabelStatement(Id("e7"), None))
    val e8 = Opt(fb.not(), LabelStatement(Id("e8"), None))
    val e9 = Opt(True, LabelStatement(Id("e9"), None))
    val c = One(CompoundStatement(List(e0, e1, e2, e3, e4, e5, e6, e7, e8, e9)))

    val env = createASTEnv(c.value)
    succ(e0, env) should be(List(e1.entry))
    succ(e1, env) should be(List(e2.entry))
    succ(e2, env) should be(List(e3.entry, e4.entry, e5.entry))
    succ(e3, env) should be(List(e6.entry, e7.entry))
    succ(e4, env) should be(List(e6.entry, e7.entry))
    succ(e5, env) should be(List(e6.entry, e7.entry))
    succ(e6, env) should be(List(e8.entry, e9.entry))
    succ(e7, env) should be(List(e8.entry, e9.entry))
    succ(e8, env) should be(List(e9.entry))
    DotGraph.map2file(getAllSucc(e0.entry, env), env.asInstanceOf[DotGraph.ASTEnv])
  }

  @Test def test_conditional_declaration_statement() {
    val e0 = Opt(True, LabelStatement(Id("e0"), None))
    val e1 = Opt(fx,
      DeclarationStatement(
        Declaration(
          List(Opt(True,IntSpecifier())),
          List(Opt(True,InitDeclaratorI(AtomicNamedDeclarator(List(),Id("k"),List()),List(),None))))))
    val e2 = Opt(fx.not(),
      DeclarationStatement(
        Declaration(
          List(Opt(True,DoubleSpecifier())),
          List(Opt(True,InitDeclaratorI(AtomicNamedDeclarator(List(),Id("k"),List()),List(),None))))))
    val e3 = Opt(True, LabelStatement(Id("e3"), None))
    val c = One(CompoundStatement(List(e0, e1, e2, e3)))

    val env = createASTEnv(c.value)
    succ(e0, env) should be(List(e1.entry, e2.entry))
    succ(e1, env) should be(List(e3.entry))
    succ(e2, env) should be(List(e3.entry))
    DotGraph.map2file(getAllSucc(e0.entry, env), env.asInstanceOf[DotGraph.ASTEnv])
  }

  @Test def test_conditional_while_statement() {
    val e0 = Opt(True, LabelStatement(Id("e0"), None))
    val e11 = Opt(True, LabelStatement(Id("e11"), None))
    val e12 = Opt(fy, LabelStatement(Id("e12"), None))
    val e1c = Id("k")
    val e1 = Opt(fx, WhileStatement(e1c, One(CompoundStatement(List(e11, e12)))))
    val e2 = Opt(True, LabelStatement(Id("e2"), None))
    val c = One(CompoundStatement(List(e0, e1, e2)))

    val env = createASTEnv(c.value)
    succ(e0, env) should be(List(e1.entry, e2.entry))
    succ(e1, env) should be(List(e1c))
    succ(e1c, env) should be(List(e11.entry, e2.entry))
    DotGraph.map2file(getAllSucc(e0.entry, env), env.asInstanceOf[DotGraph.ASTEnv])
  }

  @Test def test_conditional_for_loop() {
    val a = parseCompoundStmt("""
    {
      int k = 2;
      int i;
      for(i=0;
      #ifdef A
      i<10
      #endif
      ;i++) j++;
      int j;
    }
    """)

    val env = createASTEnv(a)
    DotGraph.map2file(getAllSucc(a, env), env.asInstanceOf[DotGraph.ASTEnv])
  }

  @Test def test_conditional_if_statement() {
    val a = parseCompoundStmt("""
    {
      int k = 3;
      if (k < 2) { k = 2; }
      #ifdef A
      else if (k < 5) { k = 5; }
      #endif
      #ifdef B
      else if (k < 7) { k = 7; }
      #endif
      else { k = 10; }
      int l = 3;
    }
    """)

    val env = createASTEnv(a)
    DotGraph.map2file(getAllSucc(a, env), env.asInstanceOf[DotGraph.ASTEnv])
  }

  @Test def test_conditional_switch_statement() {
    val a = parseCompoundStmt("""
    {
      int k = 3;
      switch (k) {
      case 1: break;
      #ifdef A
      case 2: break;
      #endif
      case 3: break;
      default: break;
      }
      int l = 2;
    }
    """)

    val env = createASTEnv(a)
    DotGraph.map2file(getAllSucc(a, env), env.asInstanceOf[DotGraph.ASTEnv])
  }

  @Test def test_conditional_for_loop_elems() {
    val e0 = Opt(True, LabelStatement(Id("e0"), None))
    val e1 = Opt(fx, ForStatement(
      Some(AssignExpr(Id("i"),"=",Constant("0"))),
      Some(AssignExpr(Id("i"),"=",Constant("2"))),
      Some(PostfixExpr(Id("i"),SimplePostfixSuffix("++"))),
      One(CompoundStatement(List(Opt(fx,ExprStatement(PostfixExpr(Id("j"),SimplePostfixSuffix("++")))))))))
    val e2 = Opt(True, LabelStatement(Id("e2"), None))
    val c = One(CompoundStatement(List(e0, e1, e2)))

    val env = createASTEnv(c)
    DotGraph.map2file(getAllSucc(e0.entry, env), env.asInstanceOf[DotGraph.ASTEnv])
  }

  @Test def test_conditional_for_loop_alternative() {
    val a = parseCompoundStmt("""
    {
      int i;
      for(;;) {
      #ifdef A
      int a;
      #else
      double a;
      #endif
      }
      int j;
    }
    """)

    val env = createASTEnv(a)
    DotGraph.map2file(getAllSucc(a, env), env.asInstanceOf[DotGraph.ASTEnv])
  }

  @Test def test_conditional_for_loop_infinite() {
    val a = parseCompoundStmt("""
    {
      int i;
      for(;;) {
      }
      int j;
    }
    """)

    val env = createASTEnv(a)
    DotGraph.map2file(getAllSucc(a, env), env.asInstanceOf[DotGraph.ASTEnv])
  }

  @Test def test_conditional_for_loop_infinite_single_statement() {
    val a = parseCompoundStmt("""
    {
      int i = 0;
      for(;;) {
        i++;
      }
      int j;
    }
    """)

    val env = createASTEnv(a)
    DotGraph.map2file(getAllSucc(a, env), env.asInstanceOf[DotGraph.ASTEnv])
  }

  @Test def test_statements_increment() {
    val a = parseCompoundStmt("""
    {
      int k = 0;
      k++;
      k++;
      k++;
    }
    """)

    val env = createASTEnv(a)
    DotGraph.map2file(getAllSucc(a, env), env.asInstanceOf[DotGraph.ASTEnv])
  }

  @Test def test_conditional_label_and_goto_statements() {
    val a = parseCompoundStmt("""
    {
      label1:
      int k;
      int l;
      if (l != 0)
        goto label1;
    }
    """)

    val env = createASTEnv(a)
    DotGraph.map2file(getAllSucc(a, env), env.asInstanceOf[DotGraph.ASTEnv])
  }

  @Test def test_conditional_statements() {
    val a = parseCompoundStmt("""
    {
      int a = 2;
      int b = 200;
      while (
      #ifdef A
      a < b
      #else
      true
      #endif
      )
      {
        a++;
        #ifdef B
        b--;
        #endif
      }
      #ifdef C
      b = 20;
      a = 30;
      #endif
      while (a > b) {
        a++;
      }
      int c;
    }
    """)

    val env = createASTEnv(a)
    DotGraph.map2file(getAllSucc(a, env), env.asInstanceOf[DotGraph.ASTEnv])
  }

  @Test def test_conditional_label_and_goto_statements2() {
    val a = parseCompoundStmt("""
    {
      goto label1;
      #ifdef A
      label1:
        int a;
      #else
      label1:
        int b;
      #endif
      label2:
    }
    """)

    val env = createASTEnv(a)
    DotGraph.map2file(getAllSucc(a, env), env.asInstanceOf[DotGraph.ASTEnv])
  }


  @Ignore def test_conditional_label_and_goto_statements_constructed() {
    val e0 = Opt(FeatureExpr.base, GotoStatement(Id("label1")))
    val e1 = Opt(fx, LabelStatement(Id("label1"), None))
    val e2 = Opt(fx, DeclarationStatement(Declaration(List(Opt(fx, IntSpecifier())), List(Opt(fx, InitDeclaratorI(AtomicNamedDeclarator(List(), Id("a"), List()), List(), None))))))
    val e3 = Opt(fx.not(), LabelStatement(Id("label1"), None))
    val e4 = Opt(fx.not(), DeclarationStatement(Declaration(List(Opt(fx.not(), IntSpecifier())), List(Opt(fx.not(), InitDeclaratorI(AtomicNamedDeclarator(List(), Id("b"), List()), List(), None))))))
    val e5 = Opt(FeatureExpr.base, LabelStatement(Id("label2"), None))
    val f = FunctionDef(List(Opt(FeatureExpr.base, VoidSpecifier())), AtomicNamedDeclarator(List(),Id("foo"),List(Opt(True,DeclIdentifierList(List())))), List(), CompoundStatement(List(e0, e1, e2, e3, e4, e5)))

    val env = createASTEnv(f)
    succ(e0, env) should be (List(e1.entry, e3.entry))
    succ(e1, env) should be (List(e2.entry))
    succ(e2, env) should be (List(e5.entry))
    succ(e3, env) should be (List(e4.entry))
    succ(e4, env) should be (List(e5.entry))
    DotGraph.map2file(getAllSucc(f, env), env.asInstanceOf[DotGraph.ASTEnv])
  }

  @Test def test_simpel_function() {
    val a = parseFunctionDef("""
    void foo() {
      #ifdef A
      int a;
      #else
      int anot;
      #endif
    }
    """)

    val env = createASTEnv(a)
    DotGraph.map2file(getAllSucc(a, env), env.asInstanceOf[DotGraph.ASTEnv])
  }

  @Test def test_liveness_simple() {
    val a = parseCompoundStmt("""
    {
      int y = 1;
      int z = y;
    }
    """)

    val env = createASTEnv(a)
    println("defines: " + defines(a))
    println("uses: " + uses(a))
  }

  @Test def test_liveness_simple_constructed() {
    val s1 = Opt(True, DeclarationStatement(Declaration(List(Opt(True, IntSpecifier())), List(Opt(True, InitDeclaratorI(AtomicNamedDeclarator(List(), Id("y"), List()), List(), Some(Initializer(None, Id("v")))))))))
    val s2 = Opt(True, DeclarationStatement(Declaration(List(Opt(True, IntSpecifier())), List(Opt(True, InitDeclaratorI(AtomicNamedDeclarator(List(), Id("z"), List()), List(), Some(Initializer(None, Id("y")))))))))
    val c = One(CompoundStatement(List(s1, s2)))

    val env = createASTEnv(c)
    println(env)
    println("in   (s1): " + in((s1, env)))
    println("out  (s1): " + out((s1, env)))
    println("in   (s2): " + in((s2, env)))
    println("out  (s2): " + out((s2, env)))
  }

  // stack overflow
  @Test def test_liveness() {
    val a = parseCompoundStmt("""
    {
      int y = v;       // s1
      int z = y;       // s2
      int x = v;       // s3
      while (x) {      // s4
        x = w;         // s41
        x = v;         // s42
      }
      return x;        // s5
    }
    """)

    val env = createASTEnv(a)
    println("in: " + in((a, env)))
    println("out: " + out((a, env)))
    println("defines: " + defines(a))
    println("uses: " + uses(a))
  }

  // stack overflow
  @Ignore def test_liveness_constructed() {
    val s1 = Opt(True, DeclarationStatement(Declaration(List(Opt(True, IntSpecifier())), List(Opt(True, InitDeclaratorI(AtomicNamedDeclarator(List(), Id("y"), List()), List(), Some(Initializer(None, Id("v")))))))))
    val s2 = Opt(fx, DeclarationStatement(Declaration(List(Opt(True, IntSpecifier())), List(Opt(True, InitDeclaratorI(AtomicNamedDeclarator(List(), Id("z"), List()), List(), Some(Initializer(None, Id("y")))))))))
    val s3 = Opt(True, DeclarationStatement(Declaration(List(Opt(True, IntSpecifier())), List(Opt(True, InitDeclaratorI(AtomicNamedDeclarator(List(), Id("x"), List()), List(), Some(Initializer(None, Id("v")))))))))
    val s41 = Opt(fy, ExprStatement(AssignExpr(Id("x"), "=", Id("w"))))
    val s42 = Opt(True, ExprStatement(AssignExpr(Id("x"), "=", Id("v"))))
    val s4 = Opt(True, WhileStatement(Id("x"), One(CompoundStatement(List(s41, s42)))))
    val s5 = Opt(True, ReturnStatement(Some(Id("x"))))
    val c = One(CompoundStatement(List(s1, s2, s3, s4, s5)))

    val env = createASTEnv(c)
    println("in      (s1): " + in((s1, env)))
    println("out     (s1): " + out((s1, env)))
    println("defines (s1): " + defines(s1))
    println("uses    (s1): " + uses(s1))
    println("#"*80)
    println("in      (s2): " + in((s2, env)))
    println("out     (s2): " + out((s2, env)))
    println("defines (s2): " + defines(s2))
    println("uses    (s2): " + uses(s2))
    println("#"*80)
    println("in      (s3): " + in((s3, env)))
    println("out     (s3): " + out((s3, env)))
    println("defines (s3): " + defines(s3))
    println("uses    (s3): " + uses(s3))
    println("#"*80)
    println("in      (s4): " + in((s4, env)))
    println("out     (s4): " + out((s4, env)))
    println("defines (s4): " + defines(s4))
    println("uses    (s4): " + uses(s4))
    println("#"*80)
    println("in      (s41): " + in((s41, env)))
    println("out     (s41): " + out((s41, env)))
    println("defines (s41): " + defines(s41))
    println("uses    (s41): " + uses(s41))
    println("#"*80)
    println("in      (s42): " + in((s42, env)))
    println("out     (s42): " + out((s42, env)))
    println("defines (s42): " + defines(s42))
    println("uses    (s42): " + uses(s42))
    println("#"*80)
    println("in      (s5): " + in((s5, env)))
    println("out     (s5): " + out((s5, env)))
    println("defines (s5): " + defines(s5))
    println("uses    (s5): " + uses(s5))
  }

  @Test def test_boa_hash() {
    val a = parseCompoundStmt("""
    {
          int i;
          hash_struct *temp;
          int total = 0;
          int count;

          for (i = 0; i < MIME_HASHTABLE_SIZE; ++i) { /* these limits OK? */
              if (mime_hashtable[i]) {
                  count = 0;
                  temp = mime_hashtable[i];
                  while (temp) {
                      temp = temp->next;
                      ++count;
                  }
      #ifdef NOISY_SIGALRM
                  log_error_time();
                  fprintf(stderr, "mime_hashtable[%d] has %d entries\n",
                          i, count);
      #endif
                  total += count;
              }
          }
          log_error_time();
          fprintf(stderr, "mime_hashtable has %d total entries\n",
                  total);

          total = 0;
          for (i = 0; i < PASSWD_HASHTABLE_SIZE; ++i) { /* these limits OK? */
              if (passwd_hashtable[i]) {
                  temp = passwd_hashtable[i];
                  count = 0;
                  while (temp) {
                      temp = temp->next;
                      ++count;
                  }
      #ifdef NOISY_SIGALRM
                  log_error_time();
                  fprintf(stderr, "passwd_hashtable[%d] has %d entries\n",
                          i, count);
      #endif
                  total += count;
              }
          }

          log_error_time();
          fprintf(stderr, "passwd_hashtable has %d total entries\n",
                  total);

      }

    """)

    val env = createASTEnv(a)
    DotGraph.map2file(getAllSucc(a, env), env.asInstanceOf[DotGraph.ASTEnv])
  }
}