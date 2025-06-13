package org.jetbrains.plugins.cucumber.java.inspections;

import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

public abstract class CucumberJavaBaseInspectionTest extends JavaCodeInsightFixtureTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();

    myFixture.addClass("""
                         package cucumber.api.java.en;
                         
                         import java.lang.annotation.ElementType;
                         import java.lang.annotation.Retention;
                         import java.lang.annotation.RetentionPolicy;
                         import java.lang.annotation.Target;
                         
                         @Retention(RetentionPolicy.RUNTIME)
                         @Target(ElementType.METHOD)
                         public @interface Given {
                             String value();
                         
                             int timeout() default 0;
                         }
                         
                         """);

    myFixture.addClass("""
                         package cucumber.api.java.en;
                         
                         import java.lang.annotation.ElementType;
                         import java.lang.annotation.Retention;
                         import java.lang.annotation.RetentionPolicy;
                         import java.lang.annotation.Target;
                         
                         @Retention(RetentionPolicy.RUNTIME)
                         @Target(ElementType.METHOD)
                         public @interface When {
                             String value();
                         
                             int timeout() default 0;
                         }
                         
                         """);

    myFixture.addClass("""
                         package cucumber.api.java.en;
                         
                         import java.lang.annotation.ElementType;
                         import java.lang.annotation.Retention;
                         import java.lang.annotation.RetentionPolicy;
                         import java.lang.annotation.Target;
                         
                         @Retention(RetentionPolicy.RUNTIME)
                         @Target(ElementType.METHOD)
                         public @interface Then {
                             String value();
                         
                             int timeout() default 0;
                         }
                         
                         """);

    myFixture.addClass("""
                         package cucumber.annotation;
                         
                         import java.lang.annotation.ElementType;
                         import java.lang.annotation.Retention;
                         import java.lang.annotation.RetentionPolicy;
                         import java.lang.annotation.Target;
                         
                         @Retention(RetentionPolicy.RUNTIME)
                         @Target(ElementType.METHOD)
                         public @interface Before {
                             /**
                              * @return a tag expression
                              */
                             String[] value() default {};
                         
                             /**
                              * @return max amount of time this is allowed to run for. 0 (default) means no restriction.
                              */
                             int timeout() default 0;
                         }
                         """);
  }
}
