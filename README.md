<img src="https://git.sleeping.town/unascribed/unsup/raw/branch/trunk/unsup.svg" align="right" width="180px">

# unsup

***Un**a's **S**imple **Up**dater*

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
additionally supports signing manifests using OpenBSD Signify for additional
security.

## For Minecraft
unsup's unique simplified design means it is compatible with **all launchers**,
from the vanilla launcher to MultiMC. Just add `-javaagent:unsup.jar` to the
JVM arguments, and place `unsup.jar` and `unsup.ini` in the .minecraft
directory.

In Prism Launcher, you can utilize unsup as a "component" by importing the
`com.unascribed.unsup.json` from the latest release. This will allow Prism
Launcher to download and manage unsup. MultiMC does not support Java agent
components.

It also has a built-in concept of *environments* and *flavors*, allowing it to
be used to manage server installs rather than just clients, and allowing users
to pick between multiple mutually incompatible mod sets.

As of 0.3.0, unsup is also capable of updating MultiMC's "components", including
unsup itself if it is added as one. This means you can update mod loaders or
Minecraft itself.

## Creating Packs
You can either point unsup at a [Packwiz](https://packwiz.infra.link/) pack.toml (recommended),
or write a native unsup manifest by hand. See [the wiki](https://git.sleeping.town/unascribed/unsup/wiki/Manifest-format) for info on the
native manifest format. The Creator GUI is on hold, as Packwiz has become a
de-facto standard for Minecraft modpacks.

## Stability
unsup has been used for modpacks on versions from 1.4.7 to 1.20.2, both client and server,
and is known to work with Forge (both legacy and modern), Fabric, Quilt, and NeoForge. It
has successfully powered many modpacks, from [small](https://git.sleeping.town/Rewind/Upsilon)
to [large](https://github.com/ModFest/bc23-pack). Native manifest and Packwiz manifest
support are both quite stable, and have been used extensively.

Signing support is still somewhat experimental, and has not been deployed by anyone at
large scale. That someone could be you — please let me know if it works well!

## GUI
unsup has a minimal and elegant GUI that works everywhere (yes, including
macOS) and can have all of its colors customized to make it fit in with your
branding. It ships with a dark theme with teal accents.

![](https://git.sleeping.town/unascribed/unsup/raw/branch/trunk/img/bootstrapping.png)

![](https://git.sleeping.town/unascribed/unsup/raw/branch/trunk/img/conflict.png)

![](https://git.sleeping.town/unascribed/unsup/raw/branch/trunk/img/update.png)

![](https://git.sleeping.town/unascribed/unsup/raw/branch/trunk/img/flavors.png)

![](https://git.sleeping.town/unascribed/unsup/raw/branch/trunk/img/done.png)

## License
The unsup agent is released under the LGPLv3. The unsup creator is released
under the GPLv3.
