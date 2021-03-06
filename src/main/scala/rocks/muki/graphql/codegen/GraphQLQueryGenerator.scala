package rocks.muki.graphql.codegen

import scala.meta._

/**
  * Provides a `GraphQLQuery` trait. This is heavily inspired by the apollo scalajs code generator.
  *
  *
  * {{{
  *   trait GraphQLQuery {
  *      // the input variables
  *      type Variables
  *
  *      // the returned data
  *      type Data
  *
  *      // the document that should be executed
  *      val document: sangria.ast.Document
  *   }
  * }}}
  */
object GraphQLQueryGenerator {

  val name = "GraphQLQuery"
  val termName: Term.Name = Term.Name(name)
  val typeName: Type.Name = Type.Name(name)

  private val traitDefinition: Defn.Trait =
    q"""trait $typeName {
          type Variables
          type Data
          val document: sangria.ast.Document
        }
     """

  /**
    * Generates the actual source code.
    *
    * @param packageName the package in which the GraphQLQuery trait should be rendered
    * @return the GraphQLTrait source code
    */
  def sourceCode(packageName: String): Pkg =
    q"""package ${ScalametaUtils.termRefOf(packageName)} {
       $traitDefinition
     }"""

  /**
    * Scala meta `Init` definitions. Use these to extend a generated class with the
    * GraphQLQuery trait.
    */
  val inits: List[Init] = List(
    Init(GraphQLQueryGenerator.typeName, Name.Anonymous(), Nil)
  )

}
