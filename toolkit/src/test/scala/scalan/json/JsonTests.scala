package scalan.json

import scalan.{BaseNestedTests, Scalan}
import spray.json.JsonFormat
import spray.json._

trait JsonTests extends BaseNestedTests {

  class JsonFormatTester[C <: Scalan](val protocol: ScalanJsonProtocol[C]) {
    import protocol._
    import ctx._

    def parse[T: JsonFormat](str: String, expected: T): Unit = {
      it(s"Parse $str") {
        val json = str.parseJson
        val parsed = json.convertTo[T]
        parsed should be(expected)
      }
    }

    def print[T: JsonFormat](e: T, expected: String): Unit = {
      it(s"Print $e") {
        val json = e.toJson
        val printed = json.prettyPrint
        printed should be(expected)
      }
    }

    def test[T: JsonFormat](e: T): Unit = {
      it(s"for $e") {
        val json = e.toJson
        val printed = json.prettyPrint
        val parsed = printed.parseJson.convertTo[T]
        parsed should be(e)
      }
    }
  }

  def getTester = {
    val tester = new JsonFormatTester(new ScalanJsonProtocol(new Scalan))
    tester
  }
}
