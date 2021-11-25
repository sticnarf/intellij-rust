/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ProjectDescriptor
import org.rust.WithDependencyRustProjectDescriptor
import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator

class MakePublicFixTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {
    fun `test make simple const public`() = checkFixByText("Make `BAR` public", """
        mod foo {
            const BAR: i32 = 10;
        }
        fn main() {
            foo::<error>BAR/*caret*/</error>;
        }
    """, """
        mod foo {
            pub(crate) const BAR: i32 = 10;
        }
        fn main() {
            foo::BAR/*caret*/;
        }
    """)

    fun `test make const with comment only public`() = checkFixByText("Make `BAR` public", """
        mod foo {
            // Only comment
            const BAR: i32 = 10;
        }
        fn main() {
            foo::<error>BAR/*caret*/</error>;
        }
    """, """
        mod foo {
            // Only comment
            pub(crate) const BAR: i32 = 10;
        }
        fn main() {
            foo::BAR/*caret*/;
        }
    """)

    fun `test make const with attribute only public`() = checkFixByText("Make `BAR` public", """
        mod foo {
            #[doc(hidden)]
            const BAR: i32 = 10;
        }
        fn main() {
            foo::<error>BAR/*caret*/</error>;
        }
    """, """
        mod foo {
            #[doc(hidden)]
            pub(crate) const BAR: i32 = 10;
        }
        fn main() {
            foo::BAR/*caret*/;
        }
    """)

    fun `test make const with comment and attribute public`() = checkFixByText("Make `BAR` public", """
        mod foo {
            // Some constant
            #[doc(hidden)]
            const BAR: i32 = 10;
        }
        fn main() {
            foo::<error>BAR/*caret*/</error>;
        }
    """, """
        mod foo {
            // Some constant
            #[doc(hidden)]
            pub(crate) const BAR: i32 = 10;
        }
        fn main() {
            foo::BAR;
        }
    """)

    fun `test make static const public`() = checkFixByText("Make `BAR` public", """
        mod foo {
            // Static constant
            #[doc(hidden)]
            static BAR: i32 = 10;
        }
        fn main() {
            &foo::<error>BAR/*caret*/</error>;
        }
    """, """
        mod foo {
            // Static constant
            #[doc(hidden)]
            pub(crate) static BAR: i32 = 10;
        }
        fn main() {
            &foo::BAR/*caret*/;
        }
    """)

    fun `test make static mut const public`() = checkFixByText("Make `BAR` public", """
        mod foo {
            // Static mut constant
            #[doc(hidden)]
            static mut BAR: i32 = 10;
        }
        fn main() {
            unsafe { &foo::<error>BAR/*caret*/</error> };
        }
    """, """
        mod foo {
            // Static mut constant
            #[doc(hidden)]
            pub(crate) static mut BAR: i32 = 10;
        }
        fn main() {
            unsafe { &foo::BAR/*caret*/ };
        }
    """)

    fun `test make simple function public`() = checkFixByText("Make `bar` public", """
        mod foo {
            fn bar() {}
        }
        fn main() {
            foo::<error>bar</error>/*caret*/();
        }
    """, """
        mod foo {
            pub(crate) fn bar() {}
        }
        fn main() {
            foo::bar/*caret*/();
        }
    """)

    fun `test make async function public`() = checkFixByText("Make `bar` public", """
        mod foo {
            async fn bar() {}
        }
        fn main() {
            foo::<error>bar/*caret*/</error>();
        }
    """, """
        mod foo {
            pub(crate) async fn bar() {}
        }
        fn main() {
            foo::bar/*caret*/();
        }
    """)

    fun `test make unsafe function public`() = checkFixByText("Make `bar` public", """
        mod foo {
            unsafe fn bar() {}
        }
        fn main() {
            unsafe { foo::<error>bar</error>/*caret*/(); };
        }
    """, """
        mod foo {
            pub(crate) unsafe fn bar() {}
        }
        fn main() {
            unsafe { foo::bar/*caret*/(); };
        }
    """)

    fun `test make const function public`() = checkFixByText("Make `bar` public", """
        mod foo {
            const fn bar() {}
        }
        fn main() {
            foo::<error>bar/*caret*/</error>();
        }
    """, """
        mod foo {
            pub(crate) const fn bar() {}
        }
        fn main() {
            foo::bar/*caret*/();
        }
    """)

    fun `test make extern function public`() = checkFixByText("Make `bar` public", """
        mod foo {
            extern "C" fn bar() {}
        }
        fn main() {
            foo::<error>bar/*caret*/</error>();
        }
    """, """
        mod foo {
            pub(crate) extern "C" fn bar() {}
        }
        fn main() {
            foo::bar/*caret*/();
        }
    """)

    fun `test make simple function with comment public`() = checkFixByText("Make `bar` public", """
        mod foo {
            // comment
            fn bar() {}
        }
        fn main() {
            foo::<error>bar</error>/*caret*/();
        }
    """, """
        mod foo {
            // comment
            pub(crate) fn bar() {}
        }
        fn main() {
            foo::bar/*caret*/();
        }
    """)

    fun `test make async function with comment and attribute public`() = checkFixByText("Make `bar` public", """
        mod foo {
            // comment and attribute
            #[doc(hidden)]
            async fn bar() {}
        }
        fn main() {
            foo::<error>bar/*caret*/</error>();
        }
    """, """
        mod foo {
            // comment and attribute
            #[doc(hidden)]
            pub(crate) async fn bar() {}
        }
        fn main() {
            foo::bar/*caret*/();
        }
    """)

    fun `test make unsafe function with attribute public`() = checkFixByText("Make `bar` public", """
        mod foo {
            #[doc(hidden)]
            unsafe fn bar() {}
        }
        fn main() {
            unsafe { foo::<error>bar</error>/*caret*/(); };
        }
    """, """
        mod foo {
            #[doc(hidden)]
            pub(crate) unsafe fn bar() {}
        }
        fn main() {
            unsafe { foo::bar/*caret*/(); };
        }
    """)

    fun `test make const function with comment public`() = checkFixByText("Make `bar` public", """
        mod foo {
            // comment
            const fn bar() {}
        }
        fn main() {
            foo::<error>bar</error>/*caret*/();
        }
    """, """
        mod foo {
            // comment
            pub(crate) const fn bar() {}
        }
        fn main() {
            foo::bar/*caret*/();
        }
    """)

    fun `test make extern function with comment and attribute public`() = checkFixByText("Make `bar` public", """
        mod foo {
            // some comment
            #[doc(hidden)]
            extern "C" fn bar() {}
        }
        fn main() {
            foo::<error>bar/*caret*/</error>();
        }
    """, """
        mod foo {
            // some comment
            #[doc(hidden)]
            pub(crate) extern "C" fn bar() {}
        }
        fn main() {
            foo::bar/*caret*/();
        }
    """)

    fun `test make inner function public`() = checkFixByText("Make `quux` public", """
        mod foo {
            pub(crate) mod bar {
                fn quux() {}
            }
        }
        fn main() {
            foo::bar::<error>quux/*caret*/</error>();
        }
    """, """
        mod foo {
            pub(crate) mod bar {
                pub(crate) fn quux() {}
            }
        }
        fn main() {
            foo::bar::quux/*caret*/();
        }
    """)

    fun `test make inner function with comment public`() = checkFixByText("Make `quux` public", """
        mod foo {
            pub(crate) mod bar {
                // Some comment
                fn quux() {}
            }
        }
        fn main() {
            foo::bar::<error>quux</error>/*caret*/();
        }
    """, """
        mod foo {
            pub(crate) mod bar {
                // Some comment
                pub(crate) fn quux() {}
            }
        }
        fn main() {
            foo::bar::quux/*caret*/();
        }
    """)

    fun `test make struct public`() = checkFixByText("Make `Bar` public", """
        mod foo {
            // Comment
            struct Bar;
        }
        fn main() {
            foo::<error>Bar/*caret*/</error>;
        }
    """, """
        mod foo {
            // Comment
            pub(crate) struct Bar;
        }
        fn main() {
            foo::Bar/*caret*/;
        }
    """)

    fun `test make simple trait public`() = checkFixByText("Make `Bar` public", """
        mod foo {
            // Some simple trait
            trait Bar {}
        }
        fn quux<T: foo::<error>Bar/*caret*/</error>>() {}
    """, """
        mod foo {
            // Some simple trait
            pub(crate) trait Bar {}
        }
        fn quux<T: foo::Bar/*caret*/>() {}
    """)

    fun `test make unsafe trait public`() = checkFixByText("Make `Bar` public", """
        mod foo {
            // Some unsafe trait
            unsafe trait Bar {}
        }
        fn quux<T: foo::<error>Bar/*caret*/</error>>() {}
    """, """
        mod foo {
            // Some unsafe trait
            pub(crate) unsafe trait Bar {}
        }
        fn quux<T: foo::Bar/*caret*/>() {}
    """)

    fun `test make auto trait public`() = checkFixByText("Make `Bar` public", """
        mod foo {
            // Some auto trait
            auto trait Bar {}
        }
        fn quux<T: foo::<error>Bar/*caret*/</error>>() {}
    """, """
        mod foo {
            // Some auto trait
            pub(crate) auto trait Bar {}
        }
        fn quux<T: foo::Bar/*caret*/>() {}
    """)

    fun `test unavailable on trait method`() = checkFixIsUnavailable("Make `foo` public", """
        mod foo {
            trait Bar {
                fn foo(&self);
            }

            impl Bar for super::S {
                fn foo(&self) {}
            }
        }
        struct S;

        fn bar(s: S) {
            s.<error>foo/*caret*/</error>();
        }
    """)

    fun `test make unsafe auto trait public`() = checkFixByText("Make `Bar` public", """
        mod foo {
            // Some unsafe auto trait
            #[doc(hidden)]
            unsafe auto trait Bar {}
        }
        fn quux<T: foo::<error>Bar/*caret*/</error>>() {}
    """, """
        mod foo {
            // Some unsafe auto trait
            #[doc(hidden)]
            pub(crate) unsafe auto trait Bar {}
        }
        fn quux<T: foo::Bar/*caret*/>() {}
    """)

    fun `test make enum public`() = checkFixByText("Make `Bar` public", """
        mod foo {
            // Comment
            #[doc(hidden)]
            enum Bar { Baz }
        }
        fn main() {
            foo::<error>Bar/*caret*/</error>::<error>Baz</error>;
        }
    """, """
        mod foo {
            // Comment
            #[doc(hidden)]
            pub(crate) enum Bar { Baz }
        }
        fn main() {
            foo::Bar/*caret*/::Baz;
        }
    """)

    fun `test make struct field public (from field lookup)`() = checkFixByText("Make `baz` public", """
        mod foo {
            pub(crate) struct Bar { baz: i32 }
            impl Bar {
                pub(crate) fn new() -> Bar { Bar { baz: 0 } }
            }
        }
        fn main() {
            let foo = foo::Bar::new();
            foo.<error>baz/*caret*/</error>;
        }
    """, """
        mod foo {
            pub(crate) struct Bar { pub(crate) baz: i32 }
            impl Bar {
                pub(crate) fn new() -> Bar { Bar { baz: 0 } }
            }
        }
        fn main() {
            let foo = foo::Bar::new();
            foo.baz/*caret*/;
        }
    """)

    fun `test make struct field public (from struct literal)`() = checkFixByText("Make `baz` public", """
        mod foo {
            pub(crate) struct Bar { baz: i32 }
        }
        fn main() {
            let foo = foo::Bar { <error>baz/*caret*/</error>: 1 };
        }
    """, """
        mod foo {
            pub(crate) struct Bar { pub(crate) baz: i32 }
        }
        fn main() {
            let foo = foo::Bar { baz: 1 };
        }
    """)

    fun `test make struct field public (from shorthand struct literal)`() = checkFixByText("Make `baz` public", """
        mod foo {
            pub(crate) struct Bar { baz: i32 }
        }
        fn main() {
            let baz: i32 = 0;
            let foo = foo::Bar { <error>baz/*caret*/</error> };
        }
    """, """
        mod foo {
            pub(crate) struct Bar { pub(crate) baz: i32 }
        }
        fn main() {
            let baz: i32 = 0;
            let foo = foo::Bar { baz };
        }
    """)

    fun `test make tuple struct field public`() = checkFixByText("Make `0` public", """
        mod foo {
            pub(crate) struct Bar(i32);
            impl Bar {
                pub(crate) fn new() -> Bar { Bar(0) }
            }
        }
        fn main() {
            let foo = foo::Bar::new();
            foo.<error>0/*caret*/</error>;
        }
    """, """
        mod foo {
            pub(crate) struct Bar(pub(crate) i32);
            impl Bar {
                pub(crate) fn new() -> Bar { Bar(0) }
            }
        }
        fn main() {
            let foo = foo::Bar::new();
            foo.0/*caret*/;
        }
    """)

    fun `test make type alias public`() = checkFixByText("Make `Bar` public", """
        mod foo {
            type Bar = i32;
        }
        fn main() {
            let foo : foo::<error>Bar/*caret*/</error> = 1;
        }
    """, """
        mod foo {
            pub(crate) type Bar = i32;
        }
        fn main() {
            let foo : foo::Bar/*caret*/ = 1;
        }
    """)

    fun `test make mod public`() = checkFixByText("Make `bar` public", """
        mod foo {
            #[doc(hidden)]
            mod bar {
                pub(crate) fn quux() {}
            }
        }
        fn main() {
            foo::<error>bar/*caret*/</error>::quux();
        }
    """, """
        mod foo {
            #[doc(hidden)]
            pub(crate) mod bar {
                pub(crate) fn quux() {}
            }
        }
        fn main() {
            foo::bar/*caret*/::quux();
        }
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test make fn from another crate public`() = checkFixByFileTree("Make `bar` public", """
    //- main.rs
        extern crate test_package;
        fn main() {
            test_package::foo::<error>bar/*caret*/</error>();
        }
    //- lib.rs
        pub mod foo {
            fn bar() {}
        }
    """, """
    //- main.rs
        extern crate test_package;
        fn main() {
            test_package::foo::bar/*caret*/();
        }
    //- lib.rs
        pub mod foo {
            pub fn bar() {}
        }
    """, stubOnly = false)

    fun `test make mod decl public`() = checkFixByFileTree("Make `bar` public", """
    //- main.rs
        mod foo {
            mod bar;
        }
        fn main() {
            foo::<error>bar/*caret*/</error>::baz();
        }
    //- foo/bar.rs
        pub fn baz() {}
    """, """
    //- main.rs
        mod foo {
            pub(crate) mod bar;
        }
        fn main() {
            foo::bar/*caret*/::baz();
        }
    //- foo/bar.rs
        pub fn baz() {}
    """, stubOnly = false)

    fun `test make public fix for restricted visibility 1`() = checkFixByText("Make `A` public", """
        pub mod foo {
            pub(self) struct A;
        }

        fn main() {
            foo::<error>A/*caret*/</error>;
        }
    """, """
        pub mod foo {
            pub(crate) struct A;
        }

        fn main() {
            foo::A/*caret*/;
        }
    """)

    fun `test make public fix for restricted visibility 2`() = checkFixByFileTree("Make `A` public", """
    //- lib.rs
        pub mod foo {
            pub(self) struct A;
        }
    //- main.rs
        extern crate test_package;
        fn main() {
            test_package::foo::<error>A/*caret*/</error>;
        }
    """, """
    //- lib.rs
        pub mod foo {
            pub struct A;
        }
    //- main.rs
        extern crate test_package;
        fn main() {
            test_package::foo::A/*caret*/;
        }
    """)
}
