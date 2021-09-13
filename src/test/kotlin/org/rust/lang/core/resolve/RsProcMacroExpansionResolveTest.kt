/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.*
import org.rust.cargo.project.workspace.CargoWorkspace.Edition.EDITION_2018
import org.rust.ide.experiments.RsExperiments.EVALUATE_BUILD_SCRIPTS
import org.rust.ide.experiments.RsExperiments.PROC_MACROS
import org.rust.lang.core.macros.MacroExpansionScope

@MinRustcVersion("1.46.0")
@MockEdition(EDITION_2018)
@ExpandMacros(MacroExpansionScope.WORKSPACE)
@WithExperimentalFeatures(EVALUATE_BUILD_SCRIPTS, PROC_MACROS)
@ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
class RsProcMacroExpansionResolveTest : RsResolveTestBase() {
    fun `test simple function-like macro`() = checkByCode("""
        use test_proc_macros::function_like_as_is;

        struct Foo;
        impl Foo {
            fn bar(&self) {}
        }     //X

        function_like_as_is! {
            fn foo() -> Foo { Foo }
        }

        fn main() {
            foo().bar()
        }       //^
    """)

    @UseNewResolve
    fun `test custom derive`() = checkByCode("""
        use test_proc_macros::DeriveImplForFoo;

        #[derive(DeriveImplForFoo)] // impl Foo { fn foo(&self) -> Bar {} }
        struct Foo;
        struct Bar;
        impl Bar {
            fn bar(&self) {}
        }     //X

        fn main() {
            Foo.foo().bar()
        }           //^
    """)

    @UseNewResolve
    fun `test custom derive dollar crate`() = checkByCode("""
        use test_proc_macros::DeriveImplForFoo;

        macro_rules! foo {
            () => {
                #[derive($ crate::DeriveImplForFoo)]
                struct Foo;
            };
        }
        foo!();
        struct Bar;
        impl Bar {
            fn bar(&self) {}
        }     //X

        fn main() {
            Foo.foo().bar()
        }           //^
    """)

    @UseNewResolve
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test custom derive in enabled cfg_attr attribute`() = checkByCode("""
        use test_proc_macros::DeriveImplForFoo;

        #[cfg_attr(intellij_rust, derive(DeriveImplForFoo))] // impl Foo { fn foo(&self) -> Bar {} }
        struct Foo;
        struct Bar;
        impl Bar {
            fn bar(&self) {}
        }    //X

        fn main() {
            Foo.foo().bar()
        }           //^
    """)

    @UseNewResolve
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test custom derive in disabled cfg_attr attribute`() = checkByCode("""
        use test_proc_macros::DeriveImplForFoo;

        #[cfg_attr(not(intellij_rust), derive(DeriveImplForFoo))] // impl Foo { fn foo(&self) -> Bar {} }
        struct Foo;
        struct Bar;
        impl Bar {
            fn bar(&self) {}
        }

        fn main() {
            Foo.foo().bar()
        }           //^ unresolved
    """)

    @UseNewResolve
    fun `test not expanded if not a custom derive macro is used in custom derive position`() = checkByCode("""
        use test_proc_macros::function_like_generates_impl_for_foo;

        #[derive(function_like_generates_impl_for_foo)] // Not a custom derive
        struct Foo;
        struct Bar;
        impl Bar {
            fn bar(&self) {}
        }

        fn main() {
            Foo.foo().bar()
        }           //^ unresolved
    """)

    @UseNewResolve
    fun `test attr legacy macro`() = checkByCode("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        struct S;

        macro_rules! foo {
            () => {};//X
        }

        foo!{}
        //^
    """)

    @UseNewResolve
    fun `test attr impl`() = checkByCode("""
        use test_proc_macros::attr_as_is;

        struct S;

        #[attr_as_is]
        impl S {
            fn foo(&self) {}
        }    //X

        fn main() {
            S.foo();
        }   //^
    """)

    @UseNewResolve
    fun `test attr mod`() = checkByCode("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        mod m {
            pub fn foo() {}
        }        //X

        fn main() {
            m::foo();
        }    //^
    """)

    @UseNewResolve
    fun `test attr fn`() = checkByCode("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        fn foo() {}
           //X
        fn main() {
            foo();
        } //^
    """)

    @UseNewResolve
    fun `test attr fn under 2 macros`() = checkByCode("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        #[attr_as_is]
        fn foo() {}
           //X
        fn main() {
            foo();
        } //^
    """)

    @UseNewResolve
    fun `test hardcoded not a macro`() = checkByCode("""
        use test_proc_macros::attr_hardcoded_not_a_macro;

        #[attr_hardcoded_not_a_macro]
        fn foo() {}
           //X
        fn main() {
            foo();
        } //^
    """)

    @UseNewResolve
    fun `test attr replaced item is unresolved`() = checkByCode("""
        use test_proc_macros::attr_replace_with_attr;

        #[attr_replace_with_attr(struct X{})]
        fn foo() {}
        fn main() {
            foo();
        } //^ unresolved
    """)

    @UseNewResolve
    fun `test attr expanded to attribute argument`() = checkByCode("""
        use test_proc_macros::attr_replace_with_attr;

        #[attr_replace_with_attr(struct X{})]
        foo! {}                       //X
        fn main() {
            let _: X;
        }        //^
    """)

    @UseNewResolve
    fun `test attr expanded from a function-like macro`() = checkByCode("""
        use test_proc_macros::attr_as_is;
        macro_rules! as_is {
            ($ i:item) => { $ i };
        }
        as_is! {
            #[attr_as_is]
            fn foo() {}
             //X
        }
        fn main() {
            foo();
        } //^
    """)

    @UseNewResolve
    fun `test function-like macro expanded from attr macro`() = checkByCode("""
        use test_proc_macros::attr_replace_with_attr;
        macro_rules! as_is {
            ($ i:item) => { $ i };
        }
        #[attr_replace_with_attr(as_is! { struct X{} })]
        foo! {}                                //X
        fn main() {
            let _: X;
        }        //^
    """)

    @UseNewResolve
    fun `test attr qualified by $crate`() = stubOnlyResolve("""
    //- lib.rs
        pub mod foo {
            pub use test_proc_macros::attr_as_is as attr_as_is_renamed;
        }
        #[macro_export]
        macro_rules! with_proc_macro {
            ($ i:item) => {
                #[$ crate::foo::attr_as_is_renamed]
                $ i
            };
        }
    //- main.rs
        use test_package::with_proc_macro;
        with_proc_macro! {
            fn foo() {}
        }
        fn main() {
            foo();
        } //^ main.rs
    """)

    @UseNewResolve
    fun `test attr qualified by $crate 2`() = stubOnlyResolve("""
    //- lib.rs
        pub mod foo {
            pub use test_proc_macros::attr_as_is as attr_as_is_renamed;
        }
        #[macro_export]
        macro_rules! with_proc_macro {
            ($ i:item) => {
                #[test_proc_macros::attr_as_is]
                #[$ crate::foo::attr_as_is_renamed]
                $ i
            };
        }
    //- main.rs
        use test_package::with_proc_macro;
        with_proc_macro! {
            fn foo() {}
        }
        fn main() {
            foo();
        } //^ main.rs
    """)

    override val followMacroExpansions: Boolean
        get() = true
}
