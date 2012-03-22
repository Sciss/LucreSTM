resolvers ++= Seq(
  "less is" at "http://repo.lessis.me",
  "coda" at "http://repo.codahale.com"
)

resolvers += Resolver.url( "sbt-plugin-releases", url( "http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases" ))( Resolver.ivyStylePatterns )

addSbtPlugin( "com.jsuereth" % "xsbt-gpg-plugin" % "0.6" )

addSbtPlugin( "me.lessis" % "ls-sbt" % "0.1.0" )
