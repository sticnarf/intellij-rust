# Kotlin

We use [Kotlin] language for the plugin. If you can program in Java, you should
be able to read and write Kotlin code right away. Kotlin is deeply similar to
Java, but has less verbose syntax and better safety. It also shares some
characteristics with Rust: type inference, immutability by default, no null
pointers (apart from those that come from Java).

If you are unsure how to implement something in Kotlin, ask a question in our
[Gitter], send a PR in Java, or use **Convert Java to Kotlin** action in the
IDE.

[Kotlin]: https://kotlinlang.org/

# Getting started

## Environment

Java 11 is required for development.
For example, you can install [openJDK](https://openjdk.java.net/install/) or [Amazon Corretto](https://docs.aws.amazon.com/corretto/latest/corretto-11-ug/downloads-list.html).
You can also [install](https://www.jetbrains.com/help/idea/sdk.html#set-up-jdk) Java just from your IntelliJ IDEA.

## Clone

```
git clone https://github.com/intellij-rust/intellij-rust.git
cd intellij-rust
```

## Building & Running

Run `RunIDEA` or `RunCLion` run configuration to build and launch development IDE (IntelliJ IDEA or CLion respectively) with the plugin.

We use gradle with [gradle-intellij](https://github.com/JetBrains/gradle-intellij-plugin) plugin to build the plugin.
It comes with a wrapper script (`gradlew` in the root of the repository) which downloads appropriate version of gradle
automatically as long as you have JDK installed.

Common Gradle tasks are:

  - `./gradlew :plugin:buildPlugin` -- fully build plugin and create an archive at
  `plugin/build/distributions` which can be installed into your IDE via `Install plugin from disk...` action found in `Settings > Plugins`.

  - `./gradlew :plugin:runIde` -- run a development IDE with the plugin installed. Can run IntelliJ IDEA or CLion.
  The particular IDE depends on `baseIDE` property in `gradle.properties`.

  - `./gradlew :test` -- more than five thousands tests. We love tests!

Note the `:` in front of the task name. The repository contains several modules that belong to two independent
plugins for Rust and TOML, which are organized as gradle subprojects. Running
`./gradlew :task` executes the task only for root module (core module of Rust plugin), `./gradlew :intellij-toml:task` will run
the task for TOML module and `./gradlew task` will do for all modules.


## Development in IntelliJ IDEA

You can get the latest IntelliJ IDEA Community Edition
[here](https://www.jetbrains.com/idea/download/), it is free.

Import the plugin project as you would do with any other gradle based project.
For example, <kbd>Ctrl + Shift + A</kbd>, `Import project` and select `build.gradle.kts` from
the root directory of the plugin.

There are `Test`, `RunIDEA`, `RunCLion` and `Generate Parser` run configurations for the most
common tasks. However, try executing `./gradlew :test` first, to download all
necessary dependencies and launch all code generation tasks. Unfortunately
during import IDEA may delete `.idea/runConfigurations`, just revert changes in
the directory if this happens.

You might want to install the following plugins:
  - [Grammar-Kit](https://plugins.jetbrains.com/plugin/6606-grammar-kit) to get highlighting for the files with BNFish grammar.
  - [PsiViewer](https://plugins.jetbrains.com/plugin/227-psiviewer) to view the AST of Rust files right in the IDE.


# Contributing

To find a problem to work on, look for
[`help wanted`](https://github.com/intellij-rust/intellij-rust/labels/help%20wanted)
issues on Github, or, even better, try to fix a problem you face yourself when
using the plugin.

When choosing an issue, you can navigate by the `E-` labels. They describe an
experience needed to solve an issue (`E-easy`, `E-medium`, `E-hard` or `E-unknown`).
The [`E-mentor`] label means that someone knows how to fix the issue and most likely
they provided some instructions about how to fix it, links to the relevant code
and so on. If you are looking for a good first issue, `E-mentor`-labeled one is the
best choice.

[`help-wanted`]: https://github.com/intellij-rust/intellij-rust/labels/help%20wanted
[`E-mentor`]: https://github.com/intellij-rust/intellij-rust/labels/E-mentor

To familiarize yourself with the plugin source code, read
the [architecture](ARCHITECTURE.md) document and look at some existing pull
requests. Please do ask questions in our [Gitter]!


Work in progress pull requests are very welcome! It is also a great way to ask
questions.

Here are some example pull requests:

  - Adding an inspection: [#713](https://github.com/intellij-rust/intellij-rust/pull/713/).

  - Adding an intention: [#318](https://github.com/intellij-rust/intellij-rust/pull/318/).

  - Adding a gutter icon: [#758](https://github.com/intellij-rust/intellij-rust/pull/758).

And also a tutorial series "Contributing to Intellij-Rust" by [@Kobzol](https://github.com/Kobzol):

  - [\#0: Intro & setup](https://kobzol.github.io/rust/intellij/2020/07/31/contributing-0-setup.html).
  - [\#1: Fixing a bug in Nest Use intention](https://kobzol.github.io/rust/intellij/2020/07/31/contributing-1-nest-use-fix.html).
  - [\#2: Intention to substitute an associated type](https://kobzol.github.io/rust/intellij/2020/08/25/contributing-2-subst-assoc-type-int.html).
  - [\#3: Quick fix to attach file to a module](https://kobzol.github.io/rust/intellij/2020/09/04/contributing-3-quick-fix-attach-file-to-mod.html).
  - [\#4: Introduce constant refactoring](https://kobzol.github.io/rust/intellij/2020/10/19/contributing-4-introduce-constant-refactoring.html).
  - [\#5: Lint attribute completion](https://kobzol.github.io/rust/intellij/2020/10/26/contributing-5-lint-attribute-completion.html).


## Code style

Please use **reformat code** action to maintain consistent style. Pay attention
to IDEA's warning and suggestions, and try to keep the code green. If you are
sure that the warning is false positive, use an annotation to suppress it.

Try to avoid copy-paste and boilerplate as much as possible. For example,
proactively use `?:` to deal with nullable values.

If you add a new file, please make sure that it contains a license preamble, as all
other files do. 


### Commit Messages

Consider prefixing commit with a `TAG:` which describes the area of the
change. Common tags are:

<details>
<summary>Tags list</summary>

  * GRAM for changes to `.bnf` files
  * PSI for other PSI related changes
  * RES for name resolution
  * TY for type inference
  * COMP for code completion
  * STUB for PSI stubs
  * MACRO for macro expansion
  
  
  * FMT for formatter
  * TYPE for editor-related functions
  * ANN for error highlighting and annotators
  * INSP for inspections
  * INT for intentions
  * REF for refactorings
  * RUN for run configurations
  * QDOC for quick documentation
  * PERF for performance optimizations
  * PRJ for project creation changes 
  * ACT for actions
  * DBG for debugger
  * LI for language injections
  * REPL for REPL integration


  * CARGO for cargo-related changes
  * GRD for build changes
  * T for tests
  * DOC for documentation
  * L10N for changes related to localization
</details>

Try to keep the summary line of a commit message under 72 characters.

# Project structure

Rust plugin sources are divided into several modules. Almost all modules (except root, `common` and `plugin` ones) support
some functionality in particular IDE or integrate with another plugin. Like debugging in CLion or
integration with `TOML` plugin.

The main goal is to separate code that can be compiled only with specific dependencies
(IDEA, CLion or another plugin) from each other. It helps to avoid accidental using
of code from wrong optional dependency.
Also, it allows us to compile and run tests of core module with different platforms
like IDEA and CLion.

The current Rust plugin modules:
* `:` - root/core module
* `:common` - shares common code between Rust and Toml plugins
* `:plugin` - module to build/run/publish Rust plugin
* `:idea` - contains code available only in IDEA
* `:clion` - contains code available only in CLion
* `:debugger` - debugger related code
* `:toml` - integration with TOML plugin
* `:intelliLang` - integration with [intelliLang](https://github.com/JetBrains/intellij-community/tree/master/plugins/IntelliLang) plugin
* `:copyright` - integration with [copyright](https://github.com/JetBrains/intellij-community/tree/master/plugins/copyright) plugin
* `:duplicates` - support `Duplicated code fragment` inspection
* `:coverage` - integration with [coverage](https://github.com/JetBrains/intellij-community/tree/master/plugins/coverage-common) plugin
* `:grazie` - integration with [grazie](https://plugins.jetbrains.com/plugin/12175-grazie) plugin 
* `:js` - interop with JavaScript language
* `:ml-completion` - integration with [Machine Learning Code Completion](https://github.com/JetBrains/intellij-community/tree/master/plugins/completion-ml-ranking) plugin

The current Toml plugin modules:
* `:intellij-toml` - module to build/run/publish Toml plugin
* `:intellij-toml:core` - core module

If you want to implement integration with another plugin/IDE, you should create a new gradle module for that.

### Platform versions

You can build the plugin for different major platform versions.
We usually support the latest release platform version and EAPs of the next one.
But each plugin artifact is compatible only with single major version.
`platformVersion` property in `gradle.properties` is used to specify major version the plugin artifact will be compatible with. 
Supported values are the same as platform [branch numbers](https://www.jetbrains.org/intellij/sdk/docs/basics/getting_started/build_number_ranges.html#intellij-platform-based-products-of-recent-ide-versions).
  

#### Source code for different platform versions

Sometimes there are not compatible changes in new platform version.
To avoid creating several parallel vcs branches for each version, we have separate
folders for each version to keep platform dependent code.

For example, current platform version is 181, next version is 182 
and `org.rust.ide.navigation.goto.RsImplsSearch` should have separate implementations
for each version. Then project source code structure will be

     +-- src
     |   +-- 181/kotlin
     |       +-- org/rust/ide/navigation/goto
     |           +-- RsImplsSearch.kt
     |   +-- 182/kotlin
     |       +-- org/rust/ide/navigation/goto
     |           +-- RsImplsSearch.kt
     |   +-- main/kotlin
     |       +-- other platfrom independent code
     
Of course, only one batch of platform dependent code will be used in compilation.   

# Testing

It is much easier to understand code changes if they are accompanied with tests.
Most tests are fixture-driven. They typically:
  1. Load a rust file that represents the initial state
  2. Execute your method under test
  3. Verify the final state, which may also be represented as a fixture


#### Structure

All test classes are placed in the `src/test/kotlin` directory. There are two
ways of providing fixtures for the tests. The first one is to put Rust files
in `src/test/resources`.

In the example below `RsFormatterTest.kt` is the test class, `blocks.rs` is
the fixture for the initial state and `blocks_after.rs` is the fixture for the
final state. It is good practice to put fixtures in the same package as tests.

     +-- src/test/kotlin
     |    +-- org/rust/ide/formatter
     |        +-- RsFormatterTest.kt
     |
     +-- src/test/resources
         +-- org/rust/ide/formatter
             +-- fixtures
                 +-- blocks.rs
                 +-- blocks_after.rs

Another way of providing fixtures is to use Kotlin's triple quoted multiline
string literals. You can get Rust syntax highlighting inside them if you have a
`@Language("Rust")` annotation applied. You can see an example
[here](https://github.com/intellij-rust/intellij-rust/blob/b5e680cc80e90523610016e662a131985aa88e56/src/test/kotlin/org/rust/ide/intentions/MoveTypeConstraintToWhereClauseIntentionTest.kt).

In general, triple quoted string fixtures should be preferred over separate Rust files.


#### Fixtures

Fixture files are very simple: they're rust code! Output fixtures on the other
hand, can be rust code over which you've run an action, HTML (for generated
documentation) or any other output you'd like to verify. Output fixtures have
the same filename as the initial fixture, but with `_after` appended.

Continuing with our example above, our initial fixture `blocks.rs` could look
like:

    pub fn main() {
    let x = {
    92
    };
    x;
    }

While our expected-output fixture `blocks_after.rs` contains:

    pub fn main() {
        let x = {
            92
        };
        x;
    }

Some tests are dependent on the position of the editor caret. Fixtures support a
special marker `<caret>` for this purpose. Multiple such markers for more
complex tests. An example of a fixture with a caret:

    pub fn main>() {
      let _ = S {
        <caret>
    };


#### Test Classes

Test classes are JUnit and written in Kotlin. They specify the resource path in
which fixtures are found and contain a number of test methods. Test methods
follow a simple convention: their name is the initial fixture name camel-cased.
For example, `RsFormatterTest.kt` would look like:

    class RsFormatterTest : FormatterTestCase() {
        override fun getTestDataPath() = "src/test/resources"
        override fun getFileExtension() = "rs"

        fun testBlocks() = stubOnlyResolve()
    }

The test method `testBlocks` states that this test uses `blocks.rs` as the
initial fixture and `blocks_after.rs` as the expected output fixture. A more
complicated fixture name like `a_longer_fixture_name.rs` would use the test
method `testALongerFixtureName()`.


# Pull requests best practices

It's much easier to review small, focused pull requests. If you can split your
changes into several pull requests then please do it. There is no such thing as
a "too small" pull request.

Here is my typical workflow for submitting a pull request. You don't need to
follow it exactly. I will show command line commands, but you can use any git
client of your choice.

First, I press the fork button on the GitHub website to fork
`https://github.com/intellij-rust/intellij-rust` to
`https://github.com/matklad/intellij-rust`. Then I clone my fork:

```
$ git clone git://github.com/matklad/intellij-rust && cd intellij-rust
```

The next thing is usually creating a branch:

```
$ git checkout -b "useful-fix"
```

I can work directly on my fork's master branch, but having a dedicated PR branch
helps if I want to synchronize my work with upstream repository or if I want to
submit several pull requests.

Usually I try to keep my PRs one commit long:

```
$ hack hack hack
$ git commit -am"(INSP): add a useful inspection"
$ ./gradlew test && git push -u origin useful-fix
```

Now I am ready to press "create pull request" button on the GitHub website!


## Incorporating code review suggestions

If my pull request consists of a single commit then to address the review I just
push additional commits to the pull request branch:

```
$ more hacking
$ git commit -am "Fix code style issues"
$ ./gradlew test && git push
```

I don't pay much attention to the commit messages, because after everything is
fine the PR will be squash merged as a single good commit.

If my PR consists of several commits, then the situation is a bit tricky. I like
to keep the history clean, so I do some form of the rebasing:

```
$ more hacking
$ git add .
$ git commit --fixup aef92cc
$ git rebase --autosquash -i HEAD~3
```

And then I force push the branch

```
$ ./gradlew test && git push --force-with-lease
```


## Updating the pull request to solve merge conflicts

If my PR starts to conflict with the upstream changes, I need to update it.
First, I add the original repository as a remote, so that I can pull changes
from it.

```
$ git remote add upstream https://github.com/intellij-rust/intellij-rust
$ git fetch upstream
$ git merge upstream/master master  # The dedicated PR branch helps a lot here.
```

Then I rebase my work on top of the updated master:

```
$ git rebase master useful-fix
```

And now I need to force push the PR branch:

```
$ ./gradlew test && git push --force-with-lease
```


[Gitter]: https://gitter.im/intellij-rust/intellij-rust
