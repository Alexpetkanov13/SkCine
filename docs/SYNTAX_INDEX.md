# SkCine syntax index

Generated from Skript's live syntax registry by `/skcine info syntax` (SkCine 1.0.0).
Every pattern below is registered and parsed by Skript; see the feature guides for descriptions and examples.

Pattern notation: `[optional]`, `(a|b)` alternatives, `%type%` expressions (`%-type%` = optional value), `1:`/`tag:` parse marks.

## Structures (2)

### StructCineScene

```
cine scene %string%
```

### StructCineSequence

```
cine sequence %string%
```

## Sections (3)

### SecAt

```
at %timespan% [(of|in) %-cinescene/cineplayback%] [(1:even if skipped|2:cosmetically)]
after %timespan% [(of|in) %-cinescene/cineplayback%] [(1:even if skipped|2:cosmetically)]
at [cine] marker %string% [(of|in) %-cinescene/cineplayback%] [(1:even if skipped|2:cosmetically)]
```

### SecChoice

```
present [a] cine choice %string% (to|for) %players/cineplaybacks% with options %strings% [with timeout %-timespan%] [defaulting to %-string%] [vote:as a vote] [menu:(in|using|via) [a|an] [inventory] menu] [pause:pausing [the] scene]
```

### SecRepeat

```
repeat every %timespan% [from %-timespan%] [(until|to) %-timespan%] [(of|in) %-cinescene/cineplayback%]
```

## Effects (39)

### EffActorAttach

```
attach [the] cine actor[s] %cineactors/entities% to %cineactor/entity% [with offset %-vector%] [rotate:and rotate with it]
detach [the] cine actor[s] %cineactors/entities%
```

### EffActorKeyframe

```
add [a] cine actor keyframe (for|to) %cineactor/entity% at %location% at %timespan% [with easing %-string%] [(in|of) %-cineplayback%]
```

### EffActorLook

```
make [the] cine actor[s] %cineactors/entities% (look at|face) %location/entity% [for %-timespan%]
```

### EffActorMove

```
move [the] cine actor[s] %cineactors/entities% to %location% (over|in) %timespan% [with easing %-string%] [facing:facing [the] movement]
```

### EffActorRemove

```
(remove|despawn) [the] cine actor[s] %cineactors%
```

### EffActorRotate

```
(rotate|turn) [the] cine actor[s] %cineactors/entities% to yaw %number% [and pitch %-number%] over %timespan% [with easing %-string%]
```

### EffActorScale

```
scale [the] cine actor[s] %cineactors/entities% to %number% over %timespan% [with easing %-string%]
```

### EffActorStop

```
stop animating [the] cine actor[s] %cineactors/entities%
```

### EffActorVisibility

```
(1:hide|2:show) [the] cine actor[s] %cineactors% [(from|to|for) %-players%]
```

### EffActorWalk

```
make [the] cine actor[s] %cineactors/entities% walk to %location% [at speed %-number%] [(giving up|timing out) after %-timespan%]
```

### EffCameraInterpolation

```
set [the] cine camera [rotation:rotation] interpolation [of %-cinescene/cineplayback%] to %string%
```

### EffCameraKeyframe

```
add [a] cine camera (keyframe|point) at %location% at %timespan% [with easing %-string%] [looking at %-location/entity%] [(to|in) %-cinescene/cineplayback%]
add [a] cine camera (keyframe|point) at %location% (to|in) %cinescene/cineplayback% at %timespan% [with easing %-string%] [looking at %-location/entity%]
```

### EffCameraShot

```
make [the] cine camera orbit [around] %location/entity% between %timespan% and %timespan% [with radius %-number%] [[and] height %-number%] [[and] (sweeping|by) %-number% degrees] [blending over %-timespan%] [(in|of) %-cinescene/cineplayback%]
make [the] cine camera follow %entity% between %timespan% and %timespan% [with offset %-vector%] [blending over %-timespan%] [(in|of) %-cinescene/cineplayback%]
make [the] cine camera (watch|track|look at) %location/entity% from %location% between %timespan% and %timespan% [blending over %-timespan%] [(in|of) %-cinescene/cineplayback%]
make [the] cine camera (hold|stay) at %location% between %timespan% and %timespan% [blending over %-timespan%] [(in|of) %-cinescene/cineplayback%]
```

### EffCineSound

```
play [the] cine sound %string% [at %-location/entity%] [with volume %-number%] [[and|with] pitch %-number%] [in [the] category %-string%] [(to|for) %-players/cineplaybacks%]
```

### EffCineTitle

```
show [a] cine title %string% [with subtitle %-string%] [to %-players/cineplaybacks%] [for %-timespan%] [with fade %-timespan%]
```

### EffControl

```
(1:pause|2:resume|3:(stop|cancel)|4:skip|5:restart) [the] cine (scene|playback)[s] (of|for) %players/cineplaybacks%
(1:pause|2:resume|3:(stop|cancel)|4:skip|5:restart) [the] cine playback[s] %cineplaybacks%
(1:pause|2:resume|3:(stop|cancel)|4:skip|5:restart) [this|the current] cine (scene|playback)
```

### EffDialogue

```
show [a] cine (dialogue|line) %string% [(from|by|spoken by) %-string%] [to %-players/cineplaybacks%] [(in|via|as) [the] (1:chat|2:title|3:subtitle|4:action[ ]bar|5:boss[ ]bar|6:text display)] [on %-cineactor/entity%] [typewriter:with [a] typewriter [effect] [at %-number% (characters|chars|letters) per second]] [words:word by word [at %-number% words per second]] [for %-timespan%] [until:until advanced] [fade:fading in [over %-timespan%]] [with typing sound %-string%]
```

### EffDialogueControl

```
advance [the] cine dialogue (of|for) %players%
(clear|cancel) [the] cine dialogue (of|for) %players%
```

### EffDisplayAnimate

```
animate [the] cine display[s] %cineactors/entities% [to] [translation %-vector%] [[and] scale %-number/vector%] [[and] rotation %-vector%] over %timespan% [with easing %-string%]
```

### EffDisplaySpin

```
spin [the] cine display[s] %cineactors/entities% around [axis] %vector% by %number% degrees over %timespan% [with easing %-string%]
spin [the] cine display[s] %cineactors/entities% around [axis] %vector% (continuously|forever) at %number% degrees per second
```

### EffDrawLine

```
draw [a] cine (1:line|1:beam|2:lightning [bolt]) of %particle/string% from %location% to %location% [with spacing %-number%] [(to|for) %-players/cineplaybacks%] [with colo[u]r %-color%]
```

### EffDrawShape

```
draw [a] cine (1:point|2:circle|3:ring|4:arc|5:sphere|6:hemisphere|7:cylinder|8:cone|9:spiral|10:helix|11:(double helix|double-helix)|12:vortex|13:line|14:wave|15:torus|16:polygon|17:star|18:heart|19:cube) (of|with) %particle/string% at %location/entity% [with radius %-number%] [[and] height %-number%] [[and] %-number% points] [rotated [by] %-number% degrees] [(to|for) %-players/cineplaybacks%] [with colo[u]r %-color%]
```

### EffEmitter

```
start [a] cine emitter [drawing] [a] (1:point|2:circle|3:ring|4:arc|5:sphere|6:hemisphere|7:cylinder|8:cone|9:spiral|10:helix|11:(double helix|double-helix)|12:vortex|13:line|14:wave|15:torus|16:polygon|17:star|18:heart|19:cube) (of|with) %particle/string% at %location/entity% [with radius %-number%] [[and] height %-number%] [[and] %-number% points] [spinning at %-number% degrees per tick] [expanding [by] %-number% per tick] [pulse:pulsing] [every %-timespan%] lasting %timespan% [(to|for) %-players/cineplaybacks%] [with colo[u]r %-color%]
```

### EffGroupRotate

```
rotate [the] cine actors %cineactors/entities% around %location% by %number% degrees over %timespan% [with easing %-string%]
```

### EffMarker

```
add [a] cine marker %string% at %timespan% [(to|in|of) %-cinescene/cineplayback%]
```

### EffPlayScene

```
(play|start) [the] cine scene %cinescene/string% (for|to) %players% [together:(together|as [a] group)]
```

### EffPlaySequence

```
play [the] cine sequence %string% [at %-timespan%]
```

### EffPreset

```
play [the] cine (preset|vfx) %string% at %location/entity% [(to|for) %-players/cineplaybacks%] [with colo[u]r %-color%] [and accent %-color%] [with scale %-number%] [lasting %-timespan%]
```

### EffRegister

```
register [the] cine scene %cinescene% [as %-string%]
unregister [the] cine scene %cinescene/string%
```

### EffRequestSkip

```
request [a] cine skip for %players%
```

### EffSceneFlag

```
make [the] cine (scene|playback) [%-cinescene/cineplayback%] [not:not] skippable
set [the] cine skip mode of [the] cine (scene|playback) [%-cinescene/cineplayback%] to %string%
```

### EffScreen

```
fade [the] cine (screen|view) (1:out|1:to black|2:in|2:from black) [with colo[u]r %-color%] [for %-players/cineplaybacks%] [over %-timespan%]
flash [the] cine (screen|view) [with colo[u]r %-color%] [for %-players/cineplaybacks%] [over %-timespan%]
```

### EffSeek

```
(seek|jump) [the] cine (scene|playback)[s] [(of|for) %-players/cineplaybacks%] to %timespan%
(seek|jump) [the] cine (scene|playback)[s] [(of|for) %-players/cineplaybacks%] to [the] [cine] marker %string%
(seek|jump) [the] cine playback[s] %cineplaybacks% to %timespan%
(seek|jump) [the] cine playback[s] %cineplaybacks% to [the] [cine] marker %string%
```

### EffShake

```
shake [the] cine camera [of %-players/cineplaybacks%] [with intensity %-number%] for %timespan% [at %-timespan%]
```

### EffStopAll

```
stop all [running] cine (scenes|playbacks)
```

### EffStopEffect

```
stop [the] cine effect[s] %cineeffects%
stop all cine effects [of %-cineplayback%]
```

### EffStopSound

```
stop [the] cine sound[s] [%-string%] [(of|for) %-players/cineplaybacks%]
```

### EffViewers

```
add %players% to [the] cine (playback|scene) [%-cineplayback%]
remove %players% from (their|his|her|its) cine (scene|playback)
restore [the] [normal] camera of %players%
```

### EffZoom

```
zoom [the] cine camera [of %-players/cineplaybacks%] [in] by %number% [blocks] over %timespan% [with easing %-string%] [at %-timespan%]
```

## Conditions (10)

### CondActor

```
%cineactors/entities% (is|are) (1:(valid|alive|spawned)|2:visible [to %-player%])
%cineactors/entities% (isn't|is not|aren't|are not) (1:(valid|alive|spawned)|2:visible [to %-player%])
```

### CondCapability

```
cine capability %string% is (available|supported)
cine capability %string% (isn't|is not) (available|supported)
```

### CondChoiceTimedOut

```
[the] cine choice timed out
[the] cine choice (didn't|did not) time out
```

### CondEffectActive

```
%cineeffects% (is|are) (active|running)
%cineeffects% (isn't|is not|aren't|are not) (active|running)
```

### CondKnown

```
cine (1:easing|2:preset|3:shape|4:interpolation) %string% is (valid|known|supported)
cine (1:easing|2:preset|3:shape|4:interpolation) %string% (isn't|is not) (valid|known|supported)
```

### CondMarker

```
%cineplaybacks% (has|have|contains) [a] [cine] marker %string%
%cineplaybacks% (doesn't|does not|don't|do not) (have|contain) [a] [cine] marker %string%
```

### CondPlaybackState

```
%cineplaybacks% (is|are) (1:playing|2:paused|3:(finished|completed)|4:(cancelled|stopped)|5:(running|active)|6:waiting for input|7:skippable)
%cineplaybacks% (isn't|is not|aren't|are not) (1:playing|2:paused|3:(finished|completed)|4:(cancelled|stopped)|5:(running|active)|6:waiting for input|7:skippable)
```

### CondPlayerCamera

```
%players% (has|have) [an] active cine camera
%players% (doesn't|does not|don't|do not) have [an] active cine camera
%players% can skip [the] [current] cine scene
%players% (can't|cannot|can not) skip [the] [current] cine scene
```

### CondSceneExists

```
cine (scene|sequence:sequence) %string% (exists|is registered)
cine (scene|sequence:sequence) %string% (doesn't|does not) exist
```

### CondWatching

```
%players% (is|are) watching [a|the] cine scene [%-cinescene/string%]
%players% (isn't|is not|aren't|are not) watching [a|the] cine scene [%-cinescene/string%]
```

## Expressions (21)

### ExprActorEntity

```
[the] [cine] actor entity of %cineactor%
[the] entity of [the] cine actor %cineactor%
```

### ExprActorName

```
[the] [cine] actor name of %cineactor%
```

### ExprActorOf

```
[the] cine actor (of|for) %entity%
[the] cine actor (named|called) %string% [(of|in) %-cineplayback%]
```

### ExprActors

```
[all [of]] [the] cine actors [of %-cineplayback%]
```

### ExprCameraLocation

```
[the] cine camera location of %player%
```

### ExprChoice

```
[the] (selected|chosen) cine (choice|option)
[the] cine choice
```

### ExprCineCount

```
[the] (number|amount|count) of (active|running) cine (1:(playbacks|scenes)|2:actors|3:effects|4:cameras|5:animations)
```

### ExprCineInfo

```
[all [of]] [the] cine (1:easings|2:presets|3:shapes|4:interpolations|5:capabilities|6:scene ids|7:sequences|8:camera backend|9:version)
[the] skcine (9:version)
```

### ExprEase

```
[the] cine ease[d value of] %number% (using|with) %string%
[the] cine lerp from %number% to %number% at %number% [(using|with) %-string%]
```

### ExprEndLocation

```
[the] cine end location [of %-cineplayback%]
```

### ExprLastEffect

```
[the] last [started] cine effect
```

### ExprNewActor

```
[a] new [public:(public|shared)] cine actor (of|as) %entitydata% at %location% [named %-string%]
[a] new [public:(public|shared)] cine item display [actor] (of|with) %itemtype% at %location% [named %-string%]
[a] new [public:(public|shared)] cine block display [actor] (of|with) %blockdata/itemtype% at %location% [named %-string%]
[a] new [public:(public|shared)] cine text display [actor] (with text|of) %string% at %location% [named %-string%]
```

### ExprNewPlayerActor

```
[a] new [public:(public|shared)] cine player actor at %location% [named %-string%] [with skin of %-player/string%] [with (name tag|nametag) %-string%]
```

### ExprPlayback

```
[the] [current] cine playback [of %-player%]
[the] last (started|played) cine playback
[all [of]] [the] (active|running|playing) cine playbacks
```

### ExprPlaybackNumber

```
[the] cine (1:progress|2:speed|3:loop (count|iteration)|4:skip votes) [of %-cineplayback%]
```

### ExprPlaybackText

```
[the] cine (1:state|2:[scene] id|3:end reason) of %cineplayback/cinescene%
```

### ExprPlaybackTime

```
[the] cine (1:duration|2:(elapsed time|position)|3:remaining time) [of %-cineplayback%]
[the] [cine] marker time of %string% [(in|of) %-cineplayback%]
```

### ExprSceneById

```
[the] [registered] cine scene (named|with id|called) %string%
[the] cine scene %string%
```

### ExprSceneVariable

```
[the] cine variable %string% [of %-cineplayback%]
```

### ExprSecNewScene

```
[a] new cine scene [(named|with id) %-string%]
```

### ExprViewers

```
[the] cine viewers [of %-cineplayback%]
```

## Events (6)

### On Cine Actor

```
cine actor (1:spawn|2:despawn|3:animation (finish|end))
```

### On Cine Camera

```
cine camera (1:attach|2:detach)
```

### On Cine Choice

```
cine choice [timeout:time[ ]out]
```

### On Cine Dialogue

```
cine dialogue (1:start|2:(finish|end))
```

### On Cine Marker

```
cine marker [%-strings%]
```

### On Cine Scene Lifecycle

```
cine scene (0:prepar(e|ation)|1:start|2:pause|3:resume|4:loop|5:(finish|complete)|6:skip|7:(cancel|stop)|8:fail[ure]|9:end) [of %-strings%]
```

---
Total patterns: 125
