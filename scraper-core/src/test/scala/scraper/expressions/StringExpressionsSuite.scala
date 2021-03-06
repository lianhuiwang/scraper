package scraper.expressions

import org.scalacheck.Gen
import org.scalacheck.Prop.forAll
import org.scalatest.prop.Checkers

import scraper.expressions.functions._
import scraper.LoggingFunSuite

class StringExpressionsSuite extends LoggingFunSuite with Checkers {
  test("concat") {
    check {
      forAll { (a: String, b: String) =>
        concat(a, b).strictlyTyped.map(_.evaluated == a + b).get
      }
    }
  }

  test("rlike") {
    check {
      forAll(Gen.alphaStr) { a: String =>
        rlike(a, "a.*").strictlyTyped.map(_.evaluated == a.startsWith("a")).get
      }
    }
  }
}
