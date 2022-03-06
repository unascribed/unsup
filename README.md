<img src="https://git.sleeping.town/unascribed/unsup/raw/branch/trunk/unsup.svg" align="right" width="180px">

# unsup

***Un**a's **S**imple **Up**dater*

**Note**: unsup is not quite complete, but it has been used to deploy and
update multiple Minecraft modpacks across Forge and Fabric for multiple
versions of Minecraft. The main missing piece right now is the Creator GUI;
manifests must be [written by hand](https://git.sleeping.town/unascribed/unsup/wiki/Manifest-format).

unsup is a somewhat minimal implementation of a generic working directory
syncer in Java. It supports running as a Java agent, to piggy-back off of the
launch of another program.

It's designed for syncing Minecraft modpacks, but its design is intentionally
generic and nothing ties it to Minecraft. unsup's GUI only shows up when there
is something to ask the user (no dialogs flashing up during launch for no
reason when everything is up to date)

An [unsup.ini](https://git.sleeping.town/unascribed/unsup/wiki/Config-format)
must be placed in the working directory for unsup to know what to do.

The updater works hard to ensure the working directory is never left in an
inconsistent state. Short of a sudden power loss in the middle of applying
changes (an incredibly short time window), an unsup update cannot result in
an inconsistent or corrupted working directory.

It does consistency validation on all downloads and on files before overwriting
them, warning the user if they've changed something that has been updated. It
has experimental support for Ed25519 signing of manifests for further security.

## For Minecraft
unsup's unique simplified design means it is compatible with **all launchers**,
from the vanilla launcher to MultiMC. Just add `-javaagent:unsup.jar` to the
JVM arguments, and place `unsup.jar` and `unsup.ini` in the .minecraft
directory.

It also has a built-in concept of *environments* and *flavors*, allowing it to
be used to manage server installs rather than just clients, and allowing users
to pick between multiple mutually incompatible mod sets.

## GUI
unsup has a minimal and elegant GUI that works everywhere (yes, including
macOS) and can have all of its colors customized to make it fit in with your
branding. It ships with a dark theme with teal accents.

![](https://git.sleeping.town/unascribed/unsup/raw/branch/trunk/img/bootstrapping.png)

![](https://git.sleeping.town/unascribed/unsup/raw/branch/trunk/img/conflict.png)

![](https://git.sleeping.town/unascribed/unsup/raw/branch/trunk/img/update.png)

![](https://git.sleeping.town/unascribed/unsup/raw/branch/trunk/img/done.png)
