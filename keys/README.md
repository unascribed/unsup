OpenBSD Signify public keys used for unsup publications.

* `release.pub`: The public side of the key used for signing explicitly-tagged (pre)releases.
* `snapshot.pub`: The public side of the key used by the CI server for signing automated snapshot builds.
* `snapshot.pub.sig`: A signature of `snapshot.pub` created with the release key, to form a chain of trust.

The snapshot key is considered "less trustworthy" than the release key, as it's stored in a less
secure fashion by virtue of being accessible by build automation. I'll make an announcement if it
ever gets compromised.

Ideally, the release key never gets compromised. I should really adopt a key rotation policy similar
to OpenBSD's, but hopefully you understand my little modpack updater tool has slightly more lax
security than an operating system.
