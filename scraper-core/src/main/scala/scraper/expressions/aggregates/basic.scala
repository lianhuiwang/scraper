package scraper.expressions.aggregates

import scraper.Name
import scraper.expressions._
import scraper.expressions.aggregates.FoldLeft.{MergeFunction, UpdateFunction}
import scraper.expressions.functions._
import scraper.expressions.typecheck.{Foldable, StrictlyTyped, TypeConstraint}
import scraper.types.{BooleanType, DataType, OrderedType}

case class Count(child: Expression) extends FoldLeft {
  override lazy val zeroValue: Expression = 0L

  override lazy val updateFunction: UpdateFunction = if (child.isNullable) {
    (count: Expression, input: Expression) => count + If(input.isNull, 0L, 1L)
  } else {
    (count: Expression, _) => count + 1L
  }

  override def mergeFunction: MergeFunction = _ + _

  override protected lazy val value = 'value of dataType.!
}

case class Max(child: Expression) extends NullableReduceLeft {
  override val updateFunction: UpdateFunction = Greatest(_, _)

  override protected def typeConstraint: TypeConstraint = children sameSubtypeOf OrderedType
}

case class Min(child: Expression) extends NullableReduceLeft {
  override val updateFunction: UpdateFunction = Least(_, _)

  override protected def typeConstraint: TypeConstraint = children sameSubtypeOf OrderedType
}

abstract class FirstLike(child: Expression, ignoresNull: Expression)
  extends DeclarativeAggregateFunction {

  override lazy val isPure: Boolean = false

  override def children: Seq[Expression] = Seq(child, ignoresNull)

  override def isNullable: Boolean = child.isNullable

  override protected def typeConstraint: TypeConstraint =
    StrictlyTyped(child :: Nil) ++ Foldable(ignoresNull :: Nil)

  override protected lazy val strictDataType: DataType = child.dataType

  protected lazy val ignoresNullBool: Boolean = ignoresNull.evaluated.asInstanceOf[Boolean]
}

case class First(child: Expression, ignoresNull: Expression) extends FirstLike(child, ignoresNull) {
  def this(child: Expression) = this(child, lit(true))

  override def nodeName: Name = "first_value"

  override lazy val stateAttributes: Seq[AttributeRef] = Seq(first, valueSet)

  override lazy val zeroValues: Seq[Expression] = Seq(Literal(null, child.dataType), false)

  override lazy val updateExpressions: Seq[Expression] =
    if (child.isNullable && ignoresNullBool) {
      Seq(
        If(!valueSet, coalesce(child, first), first),
        valueSet || child.notNull
      )
    } else {
      Seq(
        If(valueSet, first, child),
        true
      )
    }

  override lazy val mergeExpressions: Seq[Expression] = Seq(
    If(valueSet.left, first.left, first.right),
    valueSet.left || valueSet.right
  )

  override lazy val resultExpression: Expression = first

  private lazy val first = 'first of dataType withNullability isNullable

  private lazy val valueSet = 'valueSet of BooleanType.!
}

case class Last(child: Expression, ignoresNull: Expression) extends FirstLike(child, ignoresNull) {
  def this(child: Expression) = this(child, lit(true))

  override def nodeName: Name = "last_value"

  override lazy val stateAttributes: Seq[AttributeRef] = Seq(last)

  override lazy val zeroValues: Seq[Expression] = Seq(Literal(null, child.dataType))

  override lazy val updateExpressions: Seq[Expression] = Seq(
    if (child.isNullable && ignoresNullBool) coalesce(child, last) else child
  )

  override lazy val mergeExpressions: Seq[Expression] = Seq(
    coalesce(last.right, last.left)
  )

  override lazy val resultExpression: Expression = last

  private lazy val last = 'last of dataType withNullability isNullable
}
