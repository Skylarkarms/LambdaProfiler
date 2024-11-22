# LambdaProfiler
[latest version] = `1.0.0`

[dependencies]:
- [`io.github.skylarkarms:lambdas:1.0.3`](https://github.com/Skylarkarms/Lambdas)
- [`io.github.skylarkarms:concur:1.0.7`](https://github.com/Skylarkarms/Concur)
- [`io.github.skylarkarms:numberutils:1.0.0`](https://github.com/Skylarkarms/NumberUtils)

A utility library to assist in the debugging process of lambda instances.

### Implementation
In your `build.gradle` file
```groovy
repositories {
   mavenCentral()
}

dependencies {
   implementation 'io.github.skylarkarms:lambdaprofiler:[latest version]'
}
```

or in your `POM.xml`
```xml
<dependencies>
   <dependency>
      <groupId>io.github.skylarkarms</groupId>
      <artifactId>lambdaprofiler</artifactId>
      <version>[latest version]</version>
   </dependency>
</dependencies>
```