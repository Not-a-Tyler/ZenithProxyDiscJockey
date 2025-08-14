# [Disc Jockey](https://github.com/SemmieDev/Disc-Jockey) port to ZenithProxy

- This is basically just Meteor or Futures notebot but supports any instrument
- If you want songs you can look at https://noteblock.world/ or https://www.google.com/search?&q=site%3Agithub.com+filetype%3Anbs and pout .nbs files in the songs folder
- Nothing will play with just the module on, you have to do either `discJocky play <song>` or `discJocky shuffle on` to start playing songs
## Installation
1. Download the latest release from the [releases page](https://github.com/Not-a-Tyler/ZenithProxyDiscJockey/releases)
2. Put the jar file in the `plugins` folder of your ZenithProxy instance
3. Put any `.nbs` files in the `/songs` folder
4. Follow https://github.com/rfresh2/ZenithProxy/wiki/Plugins if you need help setting up plugins

## Commands
- "discJocky play <song>" - Plays a song, uses a algorithm to find the closest match
- "discJocky stop" - Stops the currently playing song
- "discJocky pause" - Pauses the currently playing song
- "discJocky resume" - Resumes the currently paused song
- "discJockey shuffle <on/off>" - Toggles shuffling of songs
- "discJockey skip" - Plays a new random song

# Demonstration Video

 https://streamable.com/dr9xld
 
# Note Block Sphere Litematic Download

https://github.com/Not-a-Tyler/ZenithProxyDiscJockey/blob/main/noteblock_sphere_cleaned.litematic