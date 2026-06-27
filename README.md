<div align="center">

# Hoshi Reader Jiten Fork

![Platform](https://img.shields.io/badge/platform-Android-lightgrey)
![License](https://img.shields.io/badge/license-GPLv3-blue)

</div>

Hoshi Reader Jiten Fork is a fork of [Hoshi Reader Android](https://github.com/HuangAntimony/Hoshi-Reader-Android) with Jiten integration added.

Hoshi Reader Android is itself a native Android recreation of [Hoshi Reader](https://github.com/Manhhao/Hoshi-Reader).

This fork is vibe-coded. It focuses mostly on integrating [JitenReader](https://github.com/Sirush/JitenReader)-style parsing, vocabulary status display, and Jiten popup actions into Hoshi Reader Android.

If other feature ideas come up, I will first try to get them integrated into the upstream Android repository when possible, so this fork can inherit them normally. This is not meant to be my personal custom version of Hoshi; it is mainly Hoshi Reader Android with Jiten added.

I do not guarantee day-to-day parity with the official Android repository.

## Jiten Setup

To enable Jiten:

1. Open `Settings`.
2. Go to `Dictionaries`.
3. Open `Settings` again from the dictionaries screen.
4. Find the `Jiten` section.
5. Enable Jiten.
6. Enter your Jiten API key.

This fork uses a different Android package name from the official app:

`com.searaw.hoshireaderjiten`

That means it installs as a separate app. It will not replace the official Hoshi Reader Android app.

Because Android treats it as a separate app, it also has separate app storage. To move your existing Hoshi data to this fork, export your books, dictionaries, TTU bookdata, and settings/backups from the official app, then import them again in this fork. You may also need to re-enter settings that are not included in your export, such as the Jiten API key.

## Jiten Features

- Choose which Jiten states are displayed, such as new, young, mature, due, mastered, blacklisted, or redundant.
- Show Jiten either as a secondary popup page or as a section above the normal dictionary definitions.
- Choose the reader marker style:
  - underline,
  - highlight,
  - colored text.
- Use standard Jiten popup actions:
  - Never forget,
  - Blacklist,
  - Forget.

## Attribution And License

This fork is based on [Hoshi Reader Android](https://github.com/HuangAntimony/Hoshi-Reader-Android), which is licensed under the GNU General Public License v3.0.

JitenReader-related behavior is based on [JitenReader](https://github.com/Sirush/JitenReader), which is licensed under the MIT License.

Jiten.moe decks, frequency lists, and derived statistical data are licensed under CC BY-SA 4.0. Jiten.moe dictionary data uses JMdict, JMnedict, and KANJIDIC data from the Electronic Dictionary Research and Development Group under the EDRDG licence.

JitenReader copyright:

- Copyright (c) 2025 chinokusari
- Copyright (c) 2026 Sirus

Distributed under the GNU General Public License v3.0. See [LICENSE](LICENSE) for details.
