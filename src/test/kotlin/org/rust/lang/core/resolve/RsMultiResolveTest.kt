/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.intellij.lang.annotations.Language
import org.rust.ExpandMacros
import org.rust.MockEdition
import org.rust.cargo.project.workspace.CargoWorkspace.Edition
import org.rust.lang.core.psi.ext.RsReferenceElement

class RsMultiResolveTest : RsResolveTestBase() {
    fun `test struct expr`() = doTest("""
        struct S { foo: i32, foo: () }
        fn main() {
            let _ = S { foo: 1 };
                       //^
        }
    """)

    fun `test struct field shorthand`() = doTest("""
        struct S { foo: i32, bar: fn() }
        fn foo() {}
        fn main() {
            let bar = 62;
            let _ = S { bar, foo };
        }                   //^
    """)

    fun `test field expr`() = doTest("""
        struct S { foo: i32, foo: () }
        fn f(s: S) {
            s.foo
             //^
        }
    """)

    fun `test use multi reference, function and mod`() = doTest("""
        use m::foo;
              //^

        mod m {
            pub fn foo() {}
            pub mod foo {}
        }
    """)

    fun `test use multi reference, duplicated function`() = doTest("""
        mod m {
            pub fn foo() {}
            pub fn foo() {}
        }
        use m::foo;
        fn main() {
            foo();
        } //^
    """)

    fun `test use multi reference, duplicated struct`() = doTest("""
        mod m {
            pub struct Foo {}
            pub struct Foo {}
        }
        use m::Foo;
        fn main() {
            let _ = Foo {};
        }         //^
    """)

    fun `test use multi reference, duplicated unit struct`() = doTest("""
        mod m {
            pub struct Foo;
            pub struct Foo;
        }
        use m::Foo;
        fn main() {
            let _ = Foo;
        }         //^
    """)

    fun `test use multi reference, duplicated enum variant`() = doTest("""
        enum E { A, A }
        use E::A;
        fn main() {
            let _ = A;
        }         //^
    """)

    @MockEdition(Edition.EDITION_2018)
    fun `test use multi reference, item in duplicated inline mod`() = doTest("""
        mod m {
            pub fn foo() {}
        }
        mod m {
            pub fn foo() {}
        }
        use m::foo;
        fn main() {
            foo();
        } //^
    """)

    @MockEdition(Edition.EDITION_2018)
    fun `test duplicated inline mod`() = doTest("""
        mod m {}
        mod m {}
        fn main() {
            m::func();
        } //^
    """)

    fun `test other mod trait bound method`() = doTest("""
        mod foo {
            pub trait Trait {
                fn foo(&self) {}
                fn foo(&self) {}
            }
        }
        fn bar<T: foo::Trait>(t: T) {
            t.foo(a);
        }   //^
    """)

    fun `test trait object inherent impl`() = doTest("""
        trait Foo { fn foo(&self){} }
        impl dyn Foo { fn foo(&self){} }
        fn foo(a: &dyn Foo){
            a.foo()
        }   //^
    """)

    @MockEdition(Edition.EDITION_2018)
    fun `test import item vs local item`() = doTest("""
        mod m {
            pub fn foo() {}
        }
        pub use crate::m::foo;
        pub fn foo() {}
        fn main() {
            foo();
        } //^
    """)

    // From https://github.com/Alexhuszagh/rust-lexical/blob/1ec9d7660e70ab731eecb3390bdf95e767548dcc/lexical-core/src/util/slice_index.rs#L82
    @ExpandMacros
    @MockEdition(Edition.EDITION_2018)
    fun `test import item vs local item (inside expanded mod)`() = doTest("""
        mod m {
            pub fn foo() {}
        }

        macro_rules! as_is { ($($ t:tt)*) => { $($ t)* }; }
        as_is! {
            mod inner {
                pub use crate::m::foo;
                pub fn foo() {}
            }
        }

        fn main() {
            inner::foo();
        }        //^
    """)

    private fun doTest(@Language("Rust") code: String) {
        InlineFile(code)
        val element = findElementInEditor<RsReferenceElement>()
        val ref = element.reference ?: error("Failed to get reference for `${element.text}`")
        check(ref.multiResolve().size == 2) {
            "Expected 2 variants, got ${ref.multiResolve()}"
        }
    }
}
