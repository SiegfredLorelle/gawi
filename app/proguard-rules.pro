# R8 keep rules for the release build.
#
# Deliberately empty of rules. Room, Hilt, kotlinx-serialization, Glance and
# WorkManager each ship their own keep rules inside their artifacts, and R8
# reads those automatically, so a rule written here on the assumption that a
# library needs one is indistinguishable afterwards from a rule that is
# load-bearing. Every rule below earned its place by fixing a failure seen on a
# device, and says which one.
#
# The shape those failures take: R8 removes or renames something reached by
# name rather than by a reference it can see, so nothing breaks at build time
# and the app throws ClassNotFoundException, NoSuchMethodException or
# SerializationException when it first reaches that path. The paths that reach
# by name here are the reflective widget action, the two WorkManager workers,
# Room's generated implementations and every serializer
# (docs/architecture.md §8 puts the verification on a device).
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
# the widget's checkbox silently stops doing anything, with no crash and no
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
