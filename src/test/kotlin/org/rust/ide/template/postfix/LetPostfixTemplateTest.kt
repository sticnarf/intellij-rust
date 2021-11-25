/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

class LetPostfixTemplateTest : RsPostfixTemplateTest(LetPostfixTemplate(RsPostfixTemplateProvider())) {
    fun `test not expr 1`() = doTestNotApplicable("""
        fn foo() {
            println!("test");.let/*caret*/
        }
    """)

    fun `test not expr 2`() = doTestNotApplicable("""
        fn foo() {
            println!("test");
        }.let/*caret*/
    """)

    fun `test simple expr`() = doTest("""
        fn foo() {
            4.let/*caret*/;
        }
    """, """
        fn foo() {
            let /*caret*/i = 4;
        }
    """)

    fun `test incomplete expr`() = doTest("""
        fn foo() {
            4.let/*caret*/
            bar();
        }
    """, """
        fn foo() {
            let /*caret*/i = 4;
            bar();
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test par expr`() = doTest("""
        fn foo() {
            (1 + 2).let/*caret*/;
        }
    """, """
        fn foo() {
            let /*caret*/i = (1 + 2);
        }
    """)

    fun `test method call expr 1`() = doTest("""
        fn foo() { }

        fn main() {
            foo().let/*caret*/
        }
    """, """
        fn foo() { }

        fn main() {
            let /*caret*/foo1 = foo();
        }
    """)

    fun `test method call expr 2`() = doTest("""
        fn foo() -> i32 { 42 }

        fn main() {
            foo().let/*caret*/
        }
    """, """
        fn foo() -> i32 { 42 }

        fn main() {
            let /*caret*/i = foo();
        }
    """)
}
