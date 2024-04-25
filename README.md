# Micromixin-remapper

Micromixin-remapper is an extension to the stianloader-remapper API to provide the ability
of remapping Mixins. Do note however that stianloader-remapper takes the liberty of remapping
mixin classes the way it chooses - which is why behaviour may change compared to tiny-remapper's
mixin extension or the official mixin annotation processor (note that unlike the
annotation processor, this library is not capable of generating refmaps. As such, the remapping
process is much more similar to tiny-remapper).

The behaviour of the remapper is done on a "best-fit" basis - that is if it cannot find a best fit,
it will fail in a fail-fast manner. Such behaviour ensures predictability and lowers user
frustration in the long run.

## Maven

This project is available under https://stianloader.org/maven/
as with the groupId of `org.stianloader` and an artifactId of `micromixin-remapper`.
Please note that the versions in the maven repository will differ from those declared
in the pom of the source code. It's best to look up the version numbers in the index
of the repository: https://stianloader.org/maven/org/stianloader/micromixin-remapper/ 

## Supported featureset

The goal of micromixin-remapper is to support everything that is supported by
micromixin-transformer. However, at this point in time micromixin-remapper only
supports the pure fundementals for the mixin specification and will likely fail
to remap almost any mixin. At the current point in time the goal of mine is to
explore the performance implications of moving away from tiny-remapper rather than
actually supporting the entire range of mixins.


Micromixin-remapper is rather likely to struggle with target selectors (often the
`method` element of a mixin handler annotation or the `@Desc` annotation) when a
single selector would apply to multiple targets. The evident workaround is to
only target a single method/field/instruction/etc. If it fails due to this issue
it will throw an `IllegalMixinException` noting what steps can be taken and why the
issue occured.

Micromixin-remapper is designed to crash in a fail-fast manner if it encounters
unknown features, but this behaviour can be overriden through
`MicromixinRemapper#logUnimplementedFeature(String)`. That being said, if it
encounters features that are known to be extremely difficult to impossible to
implement faithfully, micromixin-remapper can crash with an `IllegalMixinException`.
Do note however, that while such behaviour might be intended, it can be changed
with appropriate insight in the problem at hand.
