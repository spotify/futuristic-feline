# How to release

Add this to `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>ossrh</id>
      <username>YOUR USERNAME></username>
      <password>YOUR PASSWORD</password>
    </server>
  </servers>
</settings>
```

and

```xml
<profiles>
  <profile>
    <id>ossrh</id>
    <activation>
      <activeByDefault>true</activeByDefault>
    </activation>
    <properties>
      <gpg.executable>gpg</gpg.executable>
      <gpg.passphrase>PASSPHRASE</gpg.passphrase>
    </properties>
  </profile>
</profiles>
```

```
$ mvn release:clean release:prepare -DreleaseVersion=$NEW_VERSION
$ mvn release:perform
```
