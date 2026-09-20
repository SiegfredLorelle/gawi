# R8 keep rules for the release build.
#
# No rule here is written on speculation. Room, Hilt, kotlinx-serialization,
# Glance and WorkManager each ship their own keep rules inside their artifacts,
# and R8 reads those automatically, so a rule written here on the assumption
# that a library needs one is indistinguishable afterwards from a rule that is
# load-bearing. Every rule below earned its place by fixing a failure seen on a
# device, and says which one.
#
# Two shapes of failure earn a rule, and they are not the same defect. In the
# first, R8 removes or renames something reached by name rather than by a
# reference it can see, so nothing breaks at build time and the app throws
# ClassNotFoundException, NoSuchMethodException or SerializationException when
# it first reaches that path. The paths that reach by name here are the
# reflective widget action, the two WorkManager workers, Room's generated
# implementations and every serializer. In the second, R8 folds two classes it
# can prove interchangeable into one, and nothing throws at all: the code runs,
# and a lookup keyed on the class answers for both. Neither shape is visible to
# a build that does not shrink, which is why docs/architecture.md §8 puts the
# verification on a device.
#
# `isShrinkResources` is on beside minification. If a widget drawable or an
# appwidget-provider XML goes missing from a release build and is present in
# debug, that is resource shrinking rather than these rules, and the fix is
# either a `tools:keep` on the resource or turning the flag off in
# AndroidApplicationConventionPlugin.

# Glance builds an ActionCallback by class name through
# getDeclaredConstructor(), which R8 cannot see: it keeps the class, because a
# class literal names it, and drops the no-arg constructor, because nothing
# calls one. What that costs is the whole point of testing this on a device —
# a tap on a widget row silently stops doing anything, with no crash and no
# message, and only logcat carries
# "NoSuchMethodException: com.gawi.widget.ToggleHabitAction.<init> []".
# The tap it restores is docs/ux/widget.md §4's.
#
# Written over the interface rather than the one class: a second ActionCallback
# would fail exactly the same way, and nothing about the symptom would lead a
# reader back to this file.
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    <init>();
}

# The three GlanceAppWidget subclasses are interchangeable as far as R8 can
# see — one supertype, one shape, nothing differing but the body they compose —
# so its horizontal merger folds them into a single output class. Glance
# resolves a widget's ids by its GlanceAppWidget class and nothing else
# (GlanceAppWidgetManager.getGlanceIds takes a Class), so three merged into one
# means updateAll cannot tell them apart and one composition reaches every Gawi
# widget id. Nothing throws, so GlanceProjectionListener logs nothing and the
# failure has no tell but the picture: on a launcher the Streaks and Momo
# widgets draw the Today body after every write, and a provider-initiated
# render puts the right body back until the next write undoes it.
# docs/ux/widget.md §8 holds the measurement, and `make release` counts the
# names that survive so a toolchain change cannot bring it back unseen.
#
# The modifiers are the rule. Merging is an optimization, which a keep rule
# withholds unless it says allowoptimization; allowshrinking and
# allowobfuscation hand back the two freedoms that cost nothing here, so an
# unplaced widget can still be shrunk away and all three can still be renamed.
# `-optimizations` is not the lever — R8 ignores it.
#
# Written over the supertype for the reason the rule above is: a fourth
# GlanceAppWidget would be folded in exactly the same way, and nothing about
# the symptom would lead a reader back to this file.
-keep,allowobfuscation,allowshrinking class * extends androidx.glance.appwidget.GlanceAppWidget
