package de.frosner.gitlabtemplate

import org.scalacheck.Properties
import org.scalacheck.Prop.forAll

object DummyIntegrationTest extends Properties("String") {

  property("startsWith") = forAll { (a: String, b: String) =>
    (a+b).startsWith(a)
  }

}