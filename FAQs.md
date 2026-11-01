## Frequently Asked Question (FAQs)

This is a compilation of Frequently Asked Questions for DropFiler. This document will aid prospective and existing end-users of the software as a self-service guide in cases where the developer can't be reached. This document is subject to change and modifications at any time. Thus it'll be updated from time to time consequently to suit any new changes to the software as new developments would be implemented which might cause a discrepancy with this current document.

### What is DropFiler?

DropFiler is an Android application that enables you to save a copy of a file to your device using the Android share function or "Open with". This can be done with a single click.

DropFiler is a separately branded fork of [Phone Saver](https://github.com/ScreamingHawk/phone-saver), originally developed by Michael Standen (ScreamingHawk).

### What does "Open with DropFiler" do?

It saves a copy of the file to one of your destinations, exactly like sharing the file to DropFiler. DropFiler is not a viewer or editor and never changes, moves or deletes the original. It only appears when the app opening the file uses a standard Android "Open with" request and grants access to the file.

### Is DropFiler an update of Phone Saver?

No. DropFiler has its own application ID (`fjm.sh.dropfiler`), so it installs as a separate app next to Phone Saver. Destinations and settings are not copied over; add your destinations again in DropFiler.

### How do I set up DropFiler?

* Open the application
* Use the `Add` button to select locations to save to
* Check the `Settings` to configure the application to your needs

You can also share to DropFiler straight away: until you add a location, files are saved to
the built-in `DropFiler` folder (`/DropFiler`, created when it is first used).

### What is the "DropFiler" save location? Can I remove it?

It is a built-in destination, so there is always somewhere to save. It cannot be removed,
but you can turn on `Hide "DropFiler" save location` in Settings to hide it while you have
other locations. If you have no other location, it is still used. Files already saved there
are not touched.

### Why can't I select my SD card?

As each manufacturer manages the SD card, it is difficult to implement SD card file management in a way that works for every device. This is currently the biggest feature / bug report for DropFiler (and Phone Saver before it). 
As a work around, you can enable the `Enable location select` setting from the settings menu. This will allow you to select a location when saving a file, and should correctly handle SD cards. 

### I get an "Unsupported" message. What can I do?

If you see this message, please submit a bug report using the link provided on the screen. This will help the developer add support later. 
While you wait for development, you can try enabling the `Force saving` option in the settings. This will bypass a number of validation checks to force the file to save. There is no guarantee this option will work for you.

### Can I create a folder in DropFiler?

Yes. Use "New folder" while choosing a destination folder. DropFiler is otherwise not a file manager; use another app to rename, move or delete files. 

### Can I save multiple shared files?

Yes, if the multiple files can be shared it can be saved.

### What other information is collected when I use DropFiler?

No personal information is collected and used by the developer directly. Read the [privacy policy](Privacy.md) to understand the developer's policy towards your privacy.

### Is DropFiler available to any other operating system?

There is no intention to migrate DropFiler to another operating system.
However, the code is [permissively licensed](LICENSE) and clones are allowed so long as the license conditions are met.

### Where can I download DropFiler?

DropFiler is not published on an app store yet. You can build it from this repository (see the README). The Google Play and F-Droid listings for `link.standen.michael.phonesaver` are for the original Phone Saver app.

### Can I contribute to DropFiler?

**Yes please!**

Feel free to submit a bug or pull request for anything you feel should be added.

Phone Saver, which DropFiler is based on, has had many contributors. Changes are listed in the application [release notes](app/src/main/res/xml/changelog_master.xml), which are displayed when the application is loaded after an update.

### I have some questions not contained in this FAQs document, how can I contact the developer?

Please [open an issue](https://github.com/fjmsh/file-saver-android/issues/new) on this repository. You can also find the developer at [fjm.dev](https://fjm.dev).

Please do not contact the original Phone Saver developer about DropFiler.
