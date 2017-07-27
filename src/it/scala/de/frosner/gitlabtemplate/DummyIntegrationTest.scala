package de.frosner.gitlabtemplate

import org.scalacheck.Properties
import org.scalacheck.Prop.forAll

object DummyIntegrationTest extends Properties("String") {

  property("startsWith") = forAll { (a: String, b: String) =>
    (a + b).startsWith(a)
  }

  /*
  user = root
  pw = test1234

  curl --header "PRIVATE-TOKEN: GNvDFFr7SHzZf6Zte5Xq" "localhost/api/v4/users"
  curl --header "PRIVATE-TOKEN: GNvDFFr7SHzZf6Zte5Xq" "localhost/api/v4/users/2/keys"

  docker run --rm \
    -p 443:443 \
    -p 80:80 \
    -p 22:22 \
    -v $(pwd)/gitlab/config:/etc/gitlab \
    -v $(pwd)/gitlab/logs:/var/log/gitlab \
    -v $(pwd)/gitlab/data:/var/opt/gitlab \
    gitlab/gitlab-ce:latest
 */

}
