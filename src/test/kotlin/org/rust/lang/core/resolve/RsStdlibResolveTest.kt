/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.openapi.util.SystemInfo
import org.rust.*
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.wsl.RsWslToolchain
import org.rust.lang.core.macros.MacroExpansionScope
import org.rust.lang.core.types.infer.TypeInferenceMarks
import org.rust.stdext.BothEditions

// BACKCOMPAT: Rust 1.46
//  Since Rust 1.47 layout of stdlib was changed.
//  In general, `lib%lib_name%` was replaced with `%lib_name%/src`
@BothEditions
@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class RsStdlibResolveTest : RsResolveTestBase() {
    fun `test resolve fs`() = stubOnlyResolve("""
    //- main.rs
        use std::fs::File;
                    //^ ...libstd/fs.rs|...std/src/fs.rs

        fn main() {}
    """)

    fun `test resolve collections`() = stubOnlyResolve("""
    //- main.rs
        use std::collections::Bound;
                             //^ ...libcore/ops/range.rs|...core/src/ops/range.rs

        fn main() {}
    """)

    fun `test BTreeMap`() = stubOnlyResolve("""
    //- main.rs
        use std::collections::BTreeMap;
                                //^ ...liballoc/collections/btree/map.rs|...alloc/src/collections/btree/map.rs
    """)

    fun `test resolve core`() = stubOnlyResolve("""
    //- main.rs
        // FromStr is defined in `core` and reexported in `std`
        use std::str::FromStr;
                        //^ ...libcore/str/mod.rs|...core/src/str/mod.rs|...core/src/str/traits.rs

        fn main() { }
    """)

    fun `test resolve prelude`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            let _ = String::new();
                    //^  ...string.rs
        }
    """)

    fun `test resolve prelude in module`() = stubOnlyResolve("""
    //- main.rs
        mod tests {
            fn test() {
                let _ = String::new();
                        //^  ...string.rs
            }
        }
    """)

    fun `test resolve box`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            let _ = Box::new(92);
                   //^ ...liballoc/boxed.rs|...alloc/src/boxed.rs
        }
    """)

    fun `test don't put std in std`() = stubOnlyResolve("""
    //- main.rs
        use std::std;
                //^ unresolved
    """)

    @MaxRustcVersion("1.53.0")
    fun `test no core excludes core`() = stubOnlyResolve("""
    //- main.rs
        #![no_std]
        use core::core;
                  //^ unresolved
    """)

    fun `test no core excludes std`() = stubOnlyResolve("""
    //- main.rs
        #![no_std]
        use core::std;
                  //^ unresolved
    """)

    fun `test resolve option`() = stubOnlyResolve("""
    //- main.rs
        fn f(i: i32) -> Option<i32> {}

        fn bar() {
            if let Some(x) = f(42) {
                if let Some(y) = f(x) {
                      //^ ...libcore/option.rs|...core/src/option.rs
                    if let Some(z) = f(y) {}
                }
            }
        }
    """)

    fun `test prelude visibility 1`() = stubOnlyResolve("""
    //- main.rs
        mod m { }

        fn main() { m::Some; }
                      //^ unresolved
    """)

    fun `test prelude visibility 2`() = stubOnlyResolve("""
    //- main.rs
        mod m { }

        fn main() { use self::m::Some; }
                                //^ unresolved
    """)

    fun `test string slice resolve`() = stubOnlyResolve("""

    //- main.rs
        fn main() { "test".lines(); }
                            //^ ...str/mod.rs
    """)

    fun `test slice resolve`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            let x : [i32];
            x.iter()
             //^ ...slice/mod.rs
        }
    """)

    fun `test inherent impl char 1`() = stubOnlyResolve("""
    //- main.rs
        fn main() { 'Z'.is_lowercase(); }
                      //^ ...libcore/char/methods.rs|...core/src/char/methods.rs
    """)

    fun `test inherent impl char 2`() = stubOnlyResolve("""
    //- main.rs
        fn main() { char::is_lowercase('Z'); }
                        //^ ...libcore/char/methods.rs|...core/src/char/methods.rs
    """)

    fun `test inherent impl str 1`() = stubOnlyResolve("""
    //- main.rs
        fn main() { "Z".to_uppercase(); }
                      //^ ...str.rs
    """)

    fun `test inherent impl str 2`() = stubOnlyResolve("""
    //- main.rs
        fn main() { str::to_uppercase("Z"); }
                       //^ ...str.rs
    """)

    fun `test inherent impl f32 1`() = stubOnlyResolve("""
    //- main.rs
        fn main() { 0.0f32.sqrt(); }
                         //^ .../f32.rs
    """)

    fun `test inherent impl f32 2`() = stubOnlyResolve("""
    //- main.rs
        fn main() { f32::sqrt(0.0f32); }
                       //^ .../f32.rs
    """)

    fun `test inherent impl f32 3`() = stubOnlyResolve("""
    //- main.rs
        fn main() { <f32>::sqrt(0.0f32); }
                         //^ .../f32.rs
    """)

    fun `test inherent impl f64 1`() = stubOnlyResolve("""
    //- main.rs
        fn main() { 0.0f64.sqrt(); }
                         //^ .../f64.rs
    """)

    fun `test inherent impl f64 2`() = stubOnlyResolve("""
    //- main.rs
        fn main() { f64::sqrt(0.0f64); }
                       //^ .../f64.rs
    """)

    // BACKCOMPAT: Rust 1.41.0
    //  Since 1.42.0 some pointer methods moved from `libcore/ptr/mod.rs` to `libcore/ptr/const_ptr.rs`
    fun `test inherent impl const ptr 1`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            let p: *const char;
            p.is_null();
            //^ ...libcore/ptr/mod.rs|...libcore/ptr/const_ptr.rs|...core/src/ptr/const_ptr.rs
        }
    """)

    // BACKCOMPAT: Rust 1.41.0
    //  Since 1.42.0 some pointer methods moved from `libcore/ptr/mod.rs` to `libcore/ptr/const_ptr.rs`
    fun `test inherent impl const ptr 2`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            let p: *const char;
            <*const char>::is_null(p);
                         //^ ...libcore/ptr/mod.rs|...libcore/ptr/const_ptr.rs|...core/src/ptr/const_ptr.rs
        }
    """)

    // BACKCOMPAT: Rust 1.41.0
    //  Since 1.42.0 some pointer methods moved from `libcore/ptr/mod.rs` to `libcore/ptr/const_ptr.rs`
    fun `test inherent impl const ptr 3`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            let p: *mut char;
            <*const char>::is_null(p); //Pass a *mut pointer to a *const method
                         //^ ...libcore/ptr/mod.rs|...libcore/ptr/const_ptr.rs|...core/src/ptr/const_ptr.rs
        }
    """)

    // BACKCOMPAT: Rust 1.41.0
    //  Since 1.42.0 some pointer methods moved from `libcore/ptr/mod.rs` to `libcore/ptr/mut_ptr.rs`
    fun `test inherent impl mut ptr 1`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            let p: *mut char;
            p.is_null();
            //^ ...libcore/ptr/mod.rs|...libcore/ptr/mut_ptr.rs|...core/src/ptr/mut_ptr.rs
        }
    """)

    // BACKCOMPAT: Rust 1.41.0
    //  Since 1.42.0 some pointer methods moved from `libcore/ptr/mod.rs` to `libcore/ptr/mut_ptr.rs`
    fun `test inherent impl mut ptr 2`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            let p: *mut char;
            <*mut char>::is_null(p);
                       //^ ...libcore/ptr/mod.rs|...libcore/ptr/mut_ptr.rs|...core/src/ptr/mut_ptr.rs
        }
    """)

    fun `test println macro`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            println!("Hello, World!");
        }   //^ ...libstd/macros.rs|...std/src/macros.rs
    """)

    fun `test println macro inside doctest injection`() = stubOnlyResolve("""
    //- lib.rs
        /// ```
        /// fn main() {
        ///     println!("Hello, World!");
        /// }   //^ ...libstd/macros.rs|...std/src/macros.rs
        /// ```
        pub fn foo() {}
    """)

    fun `test assert_eq macro`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            assert_eq!("Hello, World!", "");
        }   //^ ...libcore/macros/mod.rs|...core/src/macros/mod.rs
    """)

    fun `test asm macro`() = stubOnlyResolve("""
    //- main.rs
        #![feature(asm)]
        fn main() {
            asm!("nop");
        } //^ ...libcore/macros/mod.rs|...core/src/macros/mod.rs|...core/src/lib.rs
    """)

    fun `test iterating a vec`() = stubOnlyResolve("""
    //- main.rs
        struct FooBar;
        impl FooBar { fn foo(&self) {} }

        fn foo(xs: Vec<FooBar>) {
            for x in xs {
                x.foo()
            }    //^ ...main.rs
        }
    """)

    fun `test resolve None in pattern`() = stubOnlyResolve("""
    //- main.rs
        fn foo(x: Option<i32>) -> i32 {
            match x {
                Some(v) => V,
                None => 0,
            }  //^ ...libcore/option.rs|...core/src/option.rs
        }
    """)

    fun `test array indexing`() = stubOnlyResolve("""
    //- main.rs
        struct Foo(i32);
        impl Foo { fn foo(&self) {} }

        fn main() {
            let xs = [Foo(0), Foo(123)];
            xs[0].foo();
                 //^ ...main.rs
        }
    """)

    fun `test vec indexing`() = stubOnlyResolve("""
    //- main.rs
        fn foo(xs: Vec<String>) {
            xs[0].capacity();
                 //^ ...string.rs
        }
    """)

    fun `test vec slice`() = stubOnlyResolve("""
    //- main.rs
        fn foo(xs: Vec<i32>) {
            xs[0..3].len();
                     //^ ...slice/mod.rs
        }
    """)

    fun `test resolve with defaulted type parameters`() = stubOnlyResolve("""
    //- main.rs
        use std::collections::HashSet;

        fn main() {
            let things = HashSet::new();
        }                        //^ ...hash/set.rs
    """)

    fun `test resolve with unsatisfied bounds`() = stubOnlyResolve("""
    //- main.rs
        fn main() { foo().unwrap(); }
                        //^ ...libcore/result.rs|...core/src/result.rs

        fn foo() -> Result<i32, i32> { Ok(42) }
    """)

    fun `test String plus &str`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            (String::new() + "foo").capacity();
                                     //^ ...string.rs
        }
    """)

    fun `test Instant minus Duration`() = stubOnlyResolve("""
    //- main.rs
        use std::time::{Duration, Instant};
        fn main() {
            (Instant::now() - Duration::from_secs(3)).elapsed();
                                                      //^ ...time.rs
        }
    """)

    fun `test resolve assignment operator`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            let s = String::new();
            s += "foo";
             //^ ...string.rs
        }
    """)

    fun `test resolve arithmetic operator`() = stubOnlyResolve("""
    //- main.rs
        use std::time::{Duration, Instant};
        fn main() {
            let x = Instant::now() - Duration::from_secs(3);
                                 //^ ...time.rs
        }
    """)

    fun `test autoderef Rc`() = stubOnlyResolve("""
    //- main.rs
        use std::rc::Rc;
        struct Foo;
        impl Foo { fn foo(&self) {} }

        fn main() {
            let x = Rc::new(Foo);
            x.foo()
        }    //^ ...main.rs
    """)

    fun `test generic pattern matching`() = stubOnlyResolve("""
    //- main.rs
        fn maybe() -> Option<String> { unimplemented!() }

        fn main() {
            if let Some(x) = maybe() {
                x.capacity();
                  //^ ...string.rs
            }
        }
    """)

    fun `test resolve derive traits`() {
        val traitToPath = mapOf(
            "std::marker::Clone" to "clone.rs",
            "std::marker::Copy" to "marker.rs",
            "std::fmt::Debug" to "fmt/mod.rs",
            "std::default::Default" to "default.rs",
            "std::cmp::Eq" to "cmp.rs",
            "std::hash::Hash" to "hash/mod.rs",
            "std::cmp::Ord" to "cmp.rs",
            "std::cmp::PartialEq" to "cmp.rs",
            "std::cmp::PartialOrd" to "cmp.rs"
        )
        for ((traitPath, path) in traitToPath) {
            fun check(trait: String) {
                stubOnlyResolve("""
                //- main.rs
                    #[derive($trait)]
                           ${" ".repeat(trait.length - 1)}//^ ...libcore/$path|...core/src/$path
                    struct Foo;
                """)
            }

            check(traitPath.substringAfterLast("::"))
            check(traitPath)
        }
    }

    fun `test raw identifier in derive trait`() = stubOnlyResolve("""
    //- main.rs
        #[derive(r#Debug)]
                 //^ ...libcore/fmt/mod.rs|...core/src/fmt/mod.rs
        struct Foo;
    """)

    fun `test derive trait in cfg_attr`() = stubOnlyResolve("""
    //- main.rs
        #[cfg_attr(unix, derive(Debug))]
                              //^ ...libcore/fmt/mod.rs|...core/src/fmt/mod.rs
        struct Foo;
    """)

    fun `test infer lambda expr`() = stubOnlyResolve("""
    //- main.rs
        struct S;
        impl S {
            fn foo(&self) {}
        }
        fn main() {
            let test: Vec<S> = Vec::new();
            test.into_iter().map(|a| a.foo());
        }                             //^ ...main.rs
    """)

    fun `test derivable trait method`() = stubOnlyResolve("""
    //- main.rs
        #[derive(Clone)]
        struct Foo;

        fn bar(foo: Foo) {
            let x = foo.clone();
                         //^ ...libcore/clone.rs|...core/src/clone.rs
        }
    """)

    fun `test derivable trait method with fully qualified name`() = stubOnlyResolve("""
    //- main.rs
        #[derive(std::default::Default)]
        struct Foo(i32);

        fn main() {
            let foo = Foo::default();
                          //^ ...libcore/default.rs|...core/src/default.rs
        }
    """)

    fun `test multiple derivable trait method`() = stubOnlyResolve("""
    //- main.rs
        #[derive(Debug)]
        #[derive(Clone)]
        #[derive(Hash)]
        struct Foo;

        fn bar(foo: Foo) {
            let x = foo.clone();
                         //^ ...libcore/clone.rs|...core/src/clone.rs
        }
    """)

    fun `test derivable trait method call`() = stubOnlyResolve("""
    //- main.rs
        #[derive(Clone)]
        struct Foo;
        impl Foo {
            fn foo(&self) {}
        }

        fn bar(foo: Foo) {
            let x = foo.clone();
            x.foo();
              //^ ...main.rs
        }
    """)

    fun `test ? operator with result`() = checkByCode("""
        struct S { field: u32 }
                    //X
        fn foo() -> Result<S, ()> { unimplemented!() }

        fn main() {
            let s = foo()?;
            s.field;
            //^
        }
    """)

    fun `test try operator with option`() = checkByCode("""
        struct S { field: u32 }
                    //X
        fn foo() -> Option<S> { unimplemented!() }

        fn main() {
            let s = foo()?;
            s.field;
            //^
        }
    """, TypeInferenceMarks.questionOperator)

    fun `test try! macro with aliased Result`() = checkByCode("""
        mod io {
            pub struct IoError;
            pub type IoResult<T> = Result<T, IoError>;

            pub struct S { field: u32 }
                          //X

            pub fn foo() -> IoResult<S> { unimplemented!() }

        }

        fn main() {
            let s = io::foo()?;
            s.field;
              //^
        }
    """)

    fun `test method defined in out of scope trait from prelude`() = stubOnlyResolve("""
    //- a.rs
        use super::S;
        impl Into<u8> for S { fn into(self) -> u8 { unimplemented!() } }
    //- b.rs
        use super::S;
        pub trait B where Self: Sized { fn into(self) -> u8 { unimplemented!() } }
        impl B for S {}
    //- main.rs
        mod a; mod b;
        struct S;

        fn main() {
            let _: u8 = S.into();
        }               //^ a.rs
    """, TypeInferenceMarks.methodPickTraitScope)

    fun `test &str into String`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            let _: String = "".into();
        }                    //^ ...convert/mod.rs
    """)

    fun `test resolve with no_std attribute`() = stubOnlyResolve("""
    //- main.rs
        #![no_std]

        fn foo(v: Vec) {}
                 //^ unresolved
    """)

    fun `test resolve std macro with no_std attribute`() = stubOnlyResolve("""
    //- main.rs
        #![no_std]

        fn foo() {
            println!("{}");
            //^ unresolved
        }
    """)

    fun `test resolve std macro with no_std attribute in a different file`() = stubOnlyResolve("""
    //- main.rs
        #![no_std]
        mod foo;
    //- foo.rs
        fn foo() {
            println!("{}");
            //^ unresolved
        }
    """)

    fun `test resolve core macro with no_std attribute`() = stubOnlyResolve("""
    //- main.rs
        #![no_std]

        fn foo() {
            panic!("{}");
            //^ ...libcore/macros/mod.rs|...core/src/macros/mod.rs
        }
    """)

    fun `test resolve core macro with no_core attribute`() = stubOnlyResolve("""
    //- main.rs
        #![no_core]

        fn foo() {
            panic!("{}");
            //^ unresolved
        }
    """)

    fun `test inherent impl have higher priority than derived`() = checkByCode("""
        #[derive(Clone)]
        struct S;
        impl S {
            fn clone(&self) {}
        }    //X
        fn main() {
            S.clone();
        }   //^
    """)

    @ProjectDescriptor(WithStdlibWithSymlinkRustProjectDescriptor::class)
    fun `test path to stdlib contains symlink`() = stubOnlyResolve("""
    //- main.rs
        fn foo(x: std::rc::Rc<i32>) {}
                         //^ ...liballoc/rc.rs|...alloc/src/rc.rs
    """)

    @ExpandMacros(MacroExpansionScope.ALL, "std")
    fun `test AtomicUsize`() = stubOnlyResolve("""
    //- main.rs
        use std::sync::atomic::AtomicUsize;
        fn main() {
            let a: AtomicUsize;
            a.store();
        }   //^ ...libcore/sync/atomic.rs|...core/src/sync/atomic.rs
    """)

    fun `test non-absolute std-qualified path in non-root module`() = stubOnlyResolve("""
    //- main.rs
        mod foo {
            fn main() {
                std::mem::size_of::<i32>();
            }           //^ ...libcore/mem/mod.rs|...core/src/mem/mod.rs
        }
    """)

    fun `test local 'std' module wins`() = checkByCode("""
        mod foo {
            mod std {
                pub mod mem {
                    pub fn size_of<T>() {}
                }         //X
            }
            fn main() {
                std::mem::size_of::<i32>();
            }           //^
        }
    """)

    fun `test imported 'std' module wins`() = checkByCode("""
        mod foo {
            mod bar {
                pub mod std {
                    pub mod mem {
                        pub fn size_of<T>() {}
                    }         //X
                }
            }
            use self::bar::std;
            fn main() {
                std::mem::size_of::<i32>();
            }           //^
        }
    """)

    fun `test rustc doc only macro from prelude`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            format_args!(true);
        }   //^ ...libcore/macros/mod.rs|...core/src/macros/mod.rs
    """)

    fun `test rustc doc only macro from std`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            std::format_args!(true);
        }        //^ ...libcore/macros/mod.rs|...core/src/macros/mod.rs
    """)

    fun `test f64 INFINITY`() = stubOnlyResolve("""
    //- main.rs
        use std::f64;
        fn main() {
            let a = f64::INFINITY;
        }              //^ ...num/f64.rs
    """)

    fun `test resolve concat args`() = stubOnlyResolve("""
    //- main.rs
        include!(concat!(env!("OUT_DIR"), "/bindings.rs"));
                        //^ ...libcore/macros/mod.rs|...core/src/macros/mod.rs
    """)

    // BACKCOMPAT: Rust 1.53. Paths `std/sys/%platform_name%/ext` were moved to `std/os/%platform_name%`
    @ExpandMacros(MacroExpansionScope.ALL, "actual_std")
    @ProjectDescriptor(WithActualStdlibRustProjectDescriptor::class)
    fun `test resolve in os module unix`() {
        if (!SystemInfo.isUnix && project.toolchain !is RsWslToolchain) return
        stubOnlyResolve("""
            //- main.rs
            use std::os::unix;
                        //^ ...libstd/sys/unix/ext/mod.rs|...std/src/sys/unix/ext/mod.rs|...std/src/os/unix/mod.rs
        """)
    }

    // BACKCOMPAT: Rust 1.53. Paths `std/sys/%platform_name%/ext` were moved to `std/os/%platform_name%`
    @ExpandMacros(MacroExpansionScope.ALL, "actual_std")
    @ProjectDescriptor(WithActualStdlibRustProjectDescriptor::class)
    fun `test resolve in os module windows`() {
        if (!SystemInfo.isWindows || project.toolchain is RsWslToolchain) return
        stubOnlyResolve("""
            //- main.rs
            use std::os::windows;
                        //^ ...libstd/sys/windows/ext/mod.rs|...std/src/sys/windows/ext/mod.rs|...std/src/os/windows/mod.rs
        """)
    }
}
