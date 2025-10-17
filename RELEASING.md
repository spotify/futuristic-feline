# How to release

You need to have an account setup at [http://central.sonatype.com](http://central.sonatype.com) and have rights to
publish to the `com.spotify` namespace. Then add this to `~/.m2/settings.xml`:

```xml

<settings>
  <servers>
    <server>
      <id>central</id>
      <username>username-to-maven-central</username>
      <password></password>
    </server>

```

Release using the following commands:

```
$ mvn release:clean release:prepare -DreleaseVersion=$NEW_VERSION
$ mvn release:perform -P release
```
