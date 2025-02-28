# ZenithProxy Example Plugin

[ZenithProxy](https://github.com/rfresh2/ZenithProxy) is a Minecraft proxy and bot.

This repository is an example core plugin for ZenithProxy, allowing you to add custom modules and commands.

## Installing Plugins

Plugins are only supported on the `java` ZenithProxy release channel (i.e. not `linux`).

Place plugin jars in the `plugins` folder inside the same folder as the ZenithProxy launcher.

Restart ZenithProxy to load plugins, loading after launch or hot reloading is not supported.

## Creating Plugins

Use this repository as a template to create your own plugin repository.

### Plugin Structure

Each plugin needs a main class that implements `ZenithProxyPlugin` and is annotated with `@Plugin`.

Plugin metadata like its unique id, version, and supported MC versions is defined in the `@Plugin` annotation.

[See example](https://github.com/rfresh2/ZenithProxyExamplePlugin/blob/1.21.0/src/main/java/org/example/ExamplePlugin.java)

### Plugin API

The `ZenithProxyPlugin` interface requires you to implement an `onLoad` method.

This method provides a `PluginAPI` object that you can use to register modules, commands, and config files.

`Module` and `Command` classes are implemented the same as in the ZenithProxy source code.

I recommend looking at existing modules and commands for examples.

* [Module](https://github.com/rfresh2/ZenithProxy/tree/1.21.0/src/main/java/com/zenith/module)
* [Command](https://github.com/rfresh2/ZenithProxy/tree/1.21.0/src/main/java/com/zenith/command)

### Building Plugins

Execute the Gradle `build` task: `./gradlew build` - or double-click the task in Intellij

### Testing Plugins

Execute the `run` task: `./gradlew run` - or double-click the task in Intellij

This will run ZenithProxy with your plugin loaded in the `run` directory.

