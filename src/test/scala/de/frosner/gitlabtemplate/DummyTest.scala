package de.frosner.gitlabtemplate

import org.scalacheck.Properties
import org.scalacheck.Prop.forAll

/**
  * docker run --rm \
    -p 443:443 \
    -p 80:80 \
    -p 22:22 \
    -v $(pwd)/gitlab/config:/etc/gitlab \
    -v $(pwd)/gitlab/logs:/var/log/gitlab \
    -v $(pwd)/gitlab/data:/var/opt/gitlab \
    gitlab/gitlab-ce:9.4.5-ce.0
  */
object DummyTest extends Properties("String") {

  property("substring") = forAll { (a: String, b: String, c: String) =>
    (a + b + c).substring(a.length, a.length + b.length) == b
  }

}
