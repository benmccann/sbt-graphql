
# sbt-graphql [![Build Status](https://travis-ci.org/muuki88/sbt-graphql.svg?branch=master)](https://travis-ci.org/muuki88/sbt-graphql) [ ![Download](https://api.bintray.com/packages/sbt/sbt-plugin-releases/sbt-graphql/images/download.svg) ](https://bintray.com/sbt/sbt-plugin-releases/sbt-graphql/_latestVersion) 

> This plugin is sbt 1.x only and experimental.

sbt plugin to generate and validate graphql schemas written with Sangria.

# Goals

This plugin is intended for testing pipelines that ensure that your graphql
schema and queries are intact and match. You should also be able to compare
it with another schema, e.g. the production schema, to avoid breaking changes.

# Features

All features are based on the excellent [Sangria GraphQL library](http://sangria-graphql.org)

* Code generation from `graphql` queries
* Schema generation - inspired by [mediative/sangria-codegen](https://github.com/mediative/sangria-codegen)
* Schema validation - [sangria schema validation](http://sangria-graphql.org/learn/#schema-validation)
* Schema validation against another schema - [sangria schema comparison](http://sangria-graphql.org/learn/#schema-comparison)
* Schema release note generation
* Query validation against your locally generated schema - [sangria query validation](http://sangria-graphql.org/learn/#query-validation)

# Usage

Add this to your `plugins.sbt` and replace the `<version>` placeholder with the latest release.

```scala
addSbtPlugin("rocks.muki" % "sbt-graphql" % "<version>")
```

In your `build.sbt` enable the plugins and add sangria. I'm using circe as a parser for my JSON response.

```scala
enablePlugins(GraphQLSchemaPlugin, GraphQLQueryPlugin)

libraryDependencies ++= Seq(
  "org.sangria-graphql" %% "sangria" % "1.4.2"
)
``` 

## Schema generation

The schema is generated by accessing the application code via a generated main class that renders
your schema. The main class accesses your code via a small code snippet defined in `graphqlSchemaSnippet`.

Example:
My schema is defined in an object called `ProductSchema` in a field named `schema`.
In your `build.sbt` add

```scala
graphqlSchemaSnippet := "example.ProductSchema.schema"
``` 

Now you can generate a schema with

```bash
$ sbt graphqlSchemaGen
```

You can configure the output directory in your `build.sbt` with

```scala
target in graphqlSchemaGen := target.value / "graphql-build-schema"
```

## Schema definitions

Your build can contain multiple schemas. They are stored in the `graphqlSchemas` setting.
This allows to compare arbitrary schemas, write schema.json files for each of them and validate
your queries against them.
 
There is already one schemas predefined. The `build` schema is defined by the `graphqlSchemaGen` task.
You can configure the `graphqlSchemas` label with

```sbt
name in graphqlSchemaGen := "local-build"
```

### Add a schema

Schemas are defined via a `GraphQLSchema` case class. You need to define

* a `label`. It should be unique and human readable. `prod` and `build` already exist
* a `description`. Explain where this schema comes from and what it represents
* a `schemaTask`. A sbt task that generates the schema

You can also define a schema from a `SchemaLoader`. This requires defining an anonymous sbt task.

```scala
graphqlSchemas += GraphQLSchema(
  "sangria-example",
  "staging schema at http://try.sangria-graphql.org/graphql",
  Def.task(
    GraphQLSchemaLoader
      .fromIntrospection("http://try.sangria-graphql.org/graphql", streams.value.log)
      .loadSchema()
  ).taskValue
)
```

`sbt-graphql` provides a helper object `GraphQLSchemaLoader` to load schemas from different
places.

```scala
// from a Json file
graphqlProductionSchema := GraphQLSchemaLoader
  .fromFile((resourceManaged in Compile).value / "prod.json")
  .loadSchema()

// from a GraphQL file
graphqlProductionSchema := GraphQLSchemaLoader
  .fromFile((resourceManaged in Compile).value / "prod.graphql")
  .loadSchema()

// from a graphql endpoint via introspection
graphqlProductionSchema := GraphQLSchemaLoader
  .fromIntrospection("http://prod.your-graphql.net/graphql", streams.value.log)
  .withHeaders("X-Api-Version" -> "1", "X-Api-Key" -> "4198ab84-e992-42b0-8742-225ed15a781e")
  .loadSchema()
  
// from a graphql endpoint via introspection with post request
graphqlProductionSchema := GraphQLSchemaLoader
  .fromIntrospection("http://prod.your-graphql.net/graphql", streams.value.log)
  .withPost()
  .loadSchema()
```


## Schema comparison

Sangria provides an API for comparing two Schemas. A change can be breaking or not.
The `graphqlValidateSchema` task compares two given schemas defined in the `graphqlSchemas` setting.

```bash
graphqlValidateSchema <old schema> <new schema>
```

### Example

You can compare the `build` and `prod` schema with

```bash
$ sbt
> graphqlValidateSchema build prod
```

## Schema rendering

You can render every schema with the `graphqlRenderSchema` task. In your sbt shell

```sbt
> graphqlRenderSchema build
```

This will render the `build` schema.

You can configure the target directory with

```scala
target in graphqlRenderSchema := target.value / "graphql-schema"
```

## Schema release notes

`sbt-graphql` creates release notes from changes between two schemas. The format is currently markdown.

```bash
$ sbt 
> graphqlReleaseNotes <old schema> <new schema>
```

### Example

You can create release notes for the `build` and `prod` schema with

```bash
$ sbt
> graphqlReleaseNotes build prod
```

## Code Generation

A graphql query result is usually modelled with case classes, enums and traits.
Writing these query result classes is tedious and error prone. `sbt-graphql` can
generate the correct client-side models for every graphql query.

A lot of insipration came from [apollo codegen](https://github.com/apollographql/apollo-codegen).
Make sure to check it out for scalajs, typescript and plain javascript projects.

### Configuration

Enable the code generation plugin in your `build.sbt`

```scala
enablePlugins(GraphQLCodegenPlugin)
```

You need a graphql schema for the code generation. The schema is necessary
to figure out the types for each query field. By default, the codegen plugin
looks for a schema at `src/main/resources/schema.graphql`.

We recommend to configure a graphql schema in your `graphqlSchemas` and use the task
to render the schema to a specific file.

```scala
// add a 'starwars' schema to the `graphqlSchemas` list
graphqlSchemas += GraphQLSchema(
  "starwars",
  "starwars schema at http://try.sangria-graphql.org/graphql",
  Def.task(
    GraphQLSchemaLoader
      .fromIntrospection("http://try.sangria-graphql.org/graphql", streams.value.log)
      .withHeaders("User-Agent" -> s"sbt-graphql/${version.value}")
      .loadSchema()
  ).taskValue
)

// use this schema for the code generation
graphqlCodegenSchema := graphqlRenderSchema.toTask("starwars").value
```

The `graphqlCodegenSchema` requires a `File` that points to a valid graphql schema file.
`graphqlRenderSchema` is a task that renders any given schema in the `graphqlSchemas` into
a schema file. It takes as input, the unique label that identifies the schema.
The `toTask("starwars")` invocation converts the `graphqlRenderSchema` input task with the
input parameter `starwars` to a plain `task` that can be evaluated as usual with `.value`.

By default, all `*.graphql` files in your `resourceDirectories` will be used for code generation.

### Settings

You can configure the output in various ways


* `graphqlCodegenStyle` - Configure the code output style. Default is `Apollo`.
  You can choose between [Sangria](#codegen-style-sangria) and  [Apollo](#codegen-style-apollo)
* `graphqlCodegenSchema` - The graphql schema file used for code generation
* `sourceDirectories in graphqlCodegen` - List of directories where graphql files should be looked up.
  Default is `sourceDirectory in graphqlCodegen`, which defaults to `sourceDirectory in Compile / "graphql"`
* `includeFilter in graphqlCodegen` - Filter graphql files. Default is `"*.graphql"`
* `excludeFilter in graphqlCodegen` - Filter graphql files. Default is `HiddenFileFilter || "*.fragment.graphql"`
* `graphqlCodegenQueries` - Contains all graphql query files. By default this setting contains all
  files that reside in `sourceDirectories in graphqlCodegen` and that match the `includeFilter` / `excludeFilter` settings.
* `graphqlCodegenPackage` - The package where all generated code is placed. Default is `graphql.codegen`
* `name in graphqlCodegen` - Used as a module name in the `Sangria` code generator.
* `graphqlCodegenJson` - Generate JSON encoders/decoders with your graphql query. Default is `JsonCodec.None`.
  Note that not all styles support JSON encoder/decoder generation.
* `graphqlCodegenImports: Seq[String]` - A list of additional that are included in every generated file
* `graphqlCodegenPreProcessors: Seq[PreProcessor]` - A list of preprocessors that can alter the original graphql query before it is being parsed.
   By default the `magic #imports` for including fragments are enabled. See the `magic #imports` section for more details.


### JSON support

The common serialization format for graphql results and input variables is JSON.
sbt-graphql supports JSON decoder/encoder code generation.

Supported JSON libraries and codegen styles

* Apollo style
  * [Circe](https://circe.github.io/circe/)
* Sangria style
  * _None_

In your `build.sbt` you can configure the JSON library with

```scala
graphqlCodegenJson := JsonCodec.Circe
```

### Scalar types

The code generation doesn't know about your additional scalar types.
sbt-graphql provides a setting `graphqlCodegenImports` to add an import to every
generated query object.

Example:

```
scalar ZoneDateTime
```

which is represented as `java.time.ZoneDateTime`. Add this as an import

```sbt

graphqlCodegenImports += "java.time.ZoneDateTime"
```

### Magic #imports

This is a feature tries to replicate the [apollographql/graphql-tag loader.js](https://github.com/apollographql/graphql-tag/blob/ae792b67ef16ae23a0a7a8d78af8b698e8acd7d2/loader.js#L29-L37)
feature, which enables including (or actually inlining) partials into a graphql query with magic comments.

#### Explained

The syntax is straightforward

```graphql
#import path/to/included.fragment.graphql
```

The fragment files should be named liked this

```
<name>.fragment.graphql
```

There is a `excludeFilter in graphqlCodegen`, which removes them from code generation so they are just used for inlining
and interface generation.

The resolving of paths works like this

- The path is resolved by checking all `sourceDirectories in graphqlCodegen` for the given path
- No relative paths like `./foo.fragment.graphql` are supported
- Imports are resolved recursively. This means you can `#import` fragments in a fragment.

#### Example

I have a file `CharacterInfo.fragment.graphql` which contains only a single fragment

```graphql
fragment CharacterInfo on Character {
    name
}
```

And the actual graphql query file


```graphql
query HeroFragmentQuery {
  hero {
    ...CharacterInfo
  }
  human(id: "Lea") {
    homePlanet
    ...CharacterInfo
  }
}

#import fragments/CharacterInfo.fragment.graphql
```


### Codegen style Apollo

As the name suggests the output is similar to the one in apollo codegen.

A basic `GraphQLQuery` trait is generated, which all queries extend.

```scala
trait GraphQLQuery {
  type Document
  type Variables
  type Data
}
```

The `Document` contains the query document parsed with sangria.
The `Variables` type represents the input variables for the particular query.
The `Data` type represents the shape of the query result.

For each query a new object is created with the name of the query.

Example:

```graphql
query HeroNameQuery {
  hero {
    name
  }
}
```

Generated code:

```scala
package graphql.codegen
import graphql.codegen.GraphQLQuery
import sangria.macros._
object HeroNameQuery {
  object HeroNameQuery extends GraphQLQuery {
    val document: sangria.ast.Document = graphql"""query HeroNameQuery {
  hero {
    name
  }
}"""
    case class Variables()
    case class Data(hero: Hero)
    case class Hero(name: Option[String])
  }
}
```

#### Interfaces, types and aliases

The `ApolloSourceGenerator` generates an additional file `Interfaces.scala` with the following shape:

```scala
object types {
   // contains all defined types like enums and aliases
}
// all used fragments and interfaces are generated as traits here
```

##### Use case

> Share common business logic around a fragment that shouldn't be a directive

You can now do this by defining a `fragment` and include it in every query that
requires to apply this logic. `sbt-graphql` will generate the common `trait?`,
all generated case classes will extend this fragment `trait`.

##### Limitations

You need  to **copy the fragments into every `graphql` query** that should use it.
If you have a lot of queries that reuse the fragment and you want to apply changes,
this is cumbersome.

You **cannot nest fragments**. The code generation isn't capable of naming the nested data structure. This means that you need create fragments for every nesting.

**Invalid**
```graphql
query HeroNestedFragmentQuery {
  hero {
    ...CharacterInfo
  }
  human(id: "Lea") {
    ...CharacterInfo
  }
}

# This will generate code that may compile, but is not usable
fragment CharacterInfo on Character {
    name
     friends {
        name
    }
}
```

**correct**

```graphql
query HeroNestedFragmentQuery {
  hero {
    ...CharacterInfo
  }
  human(id: "Lea") {
    ...CharacterInfo
  }
}

# create a fragment for the nested query
fragment CharacterFriends on Character {
    name
}

fragment CharacterInfo on Character {
    name
    friends {
        ...CharacterFriends
    }
}
```

### Codegen Style Sangria

This style generates one object with a specified `moduleName` and puts everything in there.

Example:

```graphql
query HeroNameQuery {
  hero {
    name
  }
}
```

Generated code:

```scala
object HeroNameQueryApi {
  case class HeroNameQuery(hero: HeroNameQueryApi.HeroNameQuery.Hero)
  object HeroNameQuery {
    case class HeroNameQueryVariables()
    case class Hero(name: Option[String])
  }
}
```
 

## Query validation

The query validation uses the schema generated with `graphqlSchemaGen` to validate against all
graphql queries defined under `src/main/graphql`. Using separated `graphql` files for queries
is inspired by [apollo codegen](https://github.com/apollographql/apollo-codegen) which generates
typings for various languages.

To validate your graphql files run

```bash
sbt graphqlValidateQueries
```

You can change the source directory for your graphql queries with this line in
your `build.sbt`

```scala
sourceDirectory in (Test, graphqlValidateQueries) := file("path/to/graphql")
```

# Developing

## Test project

You can try out your changes immediately with the `test-project`:

```bash
$ cd test-project
sbt
```

If you change code in the plugin you need to `reload` the test-project.

## Releasing

Push a tag `vX.Y.Z` a travis will automatically release it.
If you push to the `snapshot` branch a snapshot version (using the git sha)
will be published.

The `git.baseVersion := "x.y.z"` setting configures the base version for
snapshot releases.
