# The audio is not under the AGPL

Riftfall Core's code is AGPL-3.0-or-later, and the full text is in `LICENSE`. The music and sound
effects are not, which is what this file is here to explain.

The audio files (the `.ogg` files under `assets/riftfall/sounds/`) are somebody else's work. I bought
them under a separate licence, and they ship in the Riftfall Core jar with permission.

They aren't in this repository. The licence doesn't let me pass them on, and a public or shared
source tree would do exactly that, so they're kept out of Git and added locally when the release jar
is built. A jar built from a plain checkout has no audio: it still loads and plays, just silently.
The JSON that names the sounds (`sounds.json`, the boss cues) is code and is in the repository.

## Why they're split

The licence I bought the audio under is non-transferable, and it doesn't let me sublicense. The AGPL
does exactly that: it hands everyone downstream the right to redistribute and modify. Putting these
files under it would mean giving away a permission I was never given in the first place. So this
isn't caution or preference, it's the only arrangement that actually works.

## What you can do with it

Use it as part of Riftfall Core: in the jar, in a modpack that ships the mod, paid or free, it
doesn't matter. That's what the licence covers, and it's why the files are in the jar at all.

What you can't do:

- pull the audio out and ship it somewhere else, on its own or in a pack
- sell it as music or sound effects
- sublicense it
- train a generative model on it
- register it with Content ID or anything like it

The people who made it still own it.

**If you fork this, you get the code and not the audio.** The AGPL gives you every line of source.
It doesn't give you the sound files, which is why they aren't here. Don't pull them out of the jar:
find your own.

## Credit

The boss music is by Intersonic Sound. Neither licence actually asks for a credit, but both say
don't pass the work off as your own, and it would be poor form not to name them.

The audio came from Humble Bundle's "The Game Audio Collection", a GameDev Market release, under a
perpetual licence for commercial and non-commercial use.

If you made something in here and want the credit changed or the file gone, open an issue and it
will be sorted.
