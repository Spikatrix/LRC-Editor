# LRC Editor &nbsp; [![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0) ![Version: 3.2.6](https://d25lcipzij17d.cloudfront.net/badge.svg?id=gh&type=6&v=3.2.6)

<img src="https://github.com/Spikatrix/LRC-Editor/assets/12792882/c2ec19e1-bd2f-4c82-b060-4e49b02acebb" alt="LRC Editor App Icon" align="left" style="margin: 10px 20px 10px 10px; border-radius: 15%; box-shadow: 0 6px 20px 2px black">

LRC Editor is an Android app that helps you to create and edit .lrc files easily

It is available to download on [F-Droid][fdroid_page] and [GitHub][github_release_page]

<p>
	<a href="https://f-droid.org/packages/com.cg.lrceditor">
		<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80px">
	</a>
	<a href="https://github.com/Spikatrix/LRC-Editor/releases">
		<img src="https://github.com/Spikatrix/LRC-Editor/assets/12792882/cf29751a-93bf-4f47-96b2-99716653e3ba" alt="Download from GitHub" height="80px">
	</a>

</p>

## About

LRC Editor is a small, minimal Android app that helps you to create and edit .lrc (lyric) files. You can edit lyrics, tune timestamps, batch edit timestamps and much more. You can also open LRC files directly from your file manager<sup>1</sup>. The best part is that LRC Editor is completely free of ads!

You can then use LRC files in Music players, Karoke applications and more. The stock music players of major phone manufacturers like Xiaomi, Huawei, OPPO, Samsung and more support LRC files. However, not all music players supports it, for instance LG's stock media player does not support it. Check your music player's documentation to know if it supports LRC files.

**Note**: To get the best precision when syncing lyrics, use a high quality constant bitrate MP3 file or something similar. Compressed music files usually don't have accurate seek information in them which might lead to desync issues.

<sup>1</sup> [Samsung's stock file manager has issues with this](https://github.com/Spikatrix/LRC-Editor/issues/16)

## Screenshots

<img src="https://raw.githubusercontent.com/Spikatrix/LRC-Editor/master/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" height="300px"> &nbsp; &nbsp; <img src="https://raw.githubusercontent.com/Spikatrix/LRC-Editor/master/fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" height="300px">

More screenshots are available in the [F-Droid page][fdroid_page_screenshots]

## Permissions

LRC Editor requires the storage permission so that it can read and save LRC files.

Note: Android 11 brings much more stricter storage access enforcements and LRC Editor won't be able to access storage once the [`targetSdkVersion`](https://github.com/Spikatrix/LRC-Editor/blob/master/app/build.gradle#L10) is increased to 30 or above, even after granting the storage permission.

## Build from source

If you wish to build from source, clone the repo and run gradle's `assembleDebug` task:

	git clone https://github.com/Spikatrix/LRC-Editor
	cd LRC-Editor
	./gradlew assembleDebug

(Use `gradlew.bat` if you're on Windows)

Alternatively, you can clone the repo and open the project in Android Studio and then build the app from there.

There are two build flavors in this project:
 - `fdroid`: The build for [F-Droid][fdroid] which has links to LRC Editor's F-Droid page and no IAP implementation.
 - `playstore`: The build for [Google Play Store][play_store] which has links to LRC Editor's Play store page and has an IAP implementation. However, the IAPs will not work as the original keys are not exposed.

## Contributing

LRC Editor is a FOSS app developed by [me](https://github.com/Spikatrix). Contributions are always welcome.

Here are a few ways you can help:
 * Report bugs and provide suggestions via the [Issue Tracker][issue_tracker] or via [email][email_feedback]
 * Translate the app ([main][main_strings], [playstore][playstore_strings], [fdroid][fdroid_strings]) and send in the translations via a pull request or via [email][email_app_translation]
 * Tackle one of the issues/feature-requests from the [Issue Tracker][issue_tracker], make new useful features or fix bugs. In doing so, make sure that the app is still fast, minimal and easy to use.
 * Cleanup and refactor the code making it much more easier to understand and maintain.

## Translators

A big thank you to all the app translators:
 - Chinese Traditional (zh-rTW) by Martin C
 - Chinese Simplified (zh-rCW) by Krasnaya Ploshchad and Super12138
 - French (fr) by tintinmar1995
 - German (de) by Leon Thelen
 - Indonesian (in) by Fajar Maulana
 - Polish (pl) by Zbigniew Zienko
 - Portuguese (pt-rBR) by Ayrtown Karlos
 - Spanish (es) by Jonathan Martinez

And a big thank you to all the Google Play Store description translators:
 - German (de) by Leon Thelen
 - Portuguese (pt-rBR) by Ayrtown Karlos


## License

This project is licensed under the [GNU GPLv3 License][project_license]

<!-- Link references -->
[play_store_page]: https://play.google.com/store/apps/details?id=com.cg.lrceditor
[fdroid_page]: https://f-droid.org/packages/com.cg.lrceditor
[fdroid_page_screenshots]: https://f-droid.org/packages/com.cg.lrceditor#screenshots
[github_release_page]: https://github.com/Spikatrix/LRC-Editor/releases

[play_store]: https://play.google.com/store
[fdroid]: https://www.f-droid.org/

[issue_tracker]: https://github.com/Spikatrix/LRC-Editor/issues

[main_strings]: https://github.com/Spikatrix/LRC-Editor/blob/master/app/src/main/res/values/strings.xml
[playstore_strings]: https://github.com/Spikatrix/LRC-Editor/blob/master/app/src/playstore/res/values/strings.xml
[fdroid_strings]: https://github.com/Spikatrix/LRC-Editor/blob/master/app/src/fdroid/res/values/strings.xml

[email_feedback]: mailto:cg.devworks@gmail.com?subject=LRC+Editor+Feedback&body=Your+feedback+here
[email_app_translation]: mailto:cg.devworks@gmail.com?subject=LRC+Editor+Translation
[email_play_store_translation]: mailto:cg.devworks@gmail.com?subject=LRC+Editor+Play+Store+Description+Translation

[project_license]: https://github.com/Spikatrix/LRC-Editor/blob/master/LICENSE
