ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

lazy val root = (project in file("."))
  .settings(
    name := "cqs-akka-backup"
  )

lazy val akkaVersion       = "2.6.9"
lazy val leveldbVersion    = "0.7"
lazy val leveldbjniVersion = "1.8"
lazy val postgresVersion   = "42.2.2"
lazy val cassandraVersion  = "1.0.5"
lazy val json4sVersion     = "3.2.11"
lazy val protobufVersion   = "3.6.1"
lazy val scalikejdbc       = "3.5.0"

scalacOptions += "-deprecation"
resolvers += Resolver.bintrayRepo("akka", "snapshots")

libraryDependencies ++= Seq(
  "org.postgresql"      % "postgresql"                          % postgresVersion,
  "org.scalikejdbc"    %% "scalikejdbc"                         % scalikejdbc,
  "com.typesafe.akka"  %% "akka-persistence-cassandra"          % cassandraVersion,
  "com.typesafe.akka"  %% "akka-persistence-cassandra-launcher" % cassandraVersion,
  "com.typesafe.akka"  %% "akka-actor-typed"                    % akkaVersion,
  "com.typesafe.akka"  %% "akka-persistence-typed"              % akkaVersion,
  "com.typesafe.akka"  %% "akka-persistence-query"              % akkaVersion,
  "com.typesafe.slick" %% "slick-hikaricp"                      % "3.3.3",
  "com.lightbend.akka" %% "akka-stream-alpakka-slick"           % "3.0.3",


  "com.typesafe.akka"  %% "akka-persistence"              % "2.6.14",
  "com.typesafe.akka"  %% "akka-coordination"             % "2.6.14",
  "com.typesafe.akka"  %% "akka-remote"                   % "2.6.14",
  "com.typesafe.akka"  %% "akka-cluster"                  % "2.6.14",
  "com.typesafe.akka"  %% "akka-pki"                      % "2.6.14",
  "com.typesafe.akka"  %% "akka-stream-typed"             % "2.6.14",
  "com.typesafe.akka"  %% "akka-actor-typed"              % "2.6.14",
  "com.typesafe.akka"  %% "akka-persistence-query"        % "2.6.14",
  "com.typesafe.akka"  %% "akka-slf4j"                    % "2.6.14",
  "com.typesafe.akka"  %% "akka-persistence-typed"        % "2.6.14",
  "com.typesafe.akka"  %% "akka-cluster-tools"            % "2.6.14",

  //  local levelDB stores
  "org.iq80.leveldb"          % "leveldb"        % leveldbVersion,
  "org.fusesource.leveldbjni" % "leveldbjni-all" % leveldbjniVersion
)

libraryDependencies += "com.typesafe"   % "config"          % "1.4.0"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"
