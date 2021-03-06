package dev.latvian.kubejs.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.latvian.kubejs.KubeJS;
import dev.latvian.kubejs.docs.ID;
import dev.latvian.kubejs.server.ServerJS;
import dev.latvian.kubejs.text.Text;
import dev.latvian.kubejs.text.TextString;
import dev.latvian.kubejs.text.TextTranslate;
import dev.latvian.kubejs.world.WorldJS;
import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.nbt.*;
import net.minecraft.stat.Stat;
import net.minecraft.stat.Stats;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

/**
 * @author LatvianModder
 */
public class UtilsJS {
	public static final Random RANDOM = new Random();
	
	public static void init() {
	}
	
	@SuppressWarnings("unchecked")
	public static <T> T cast(Object o) {
		return (T) o;
	}
	
	public static void queueIO(Runnable runnable) {
		/*FIXME: ThreadedFileIOBase.getThreadedIOInstance().queueIO(() -> {

			try
			{
				runnable.run();
			}
			catch (Exception ex)
			{
				ex.printStackTrace();
			}

			return false;
		});
		 */
		
		try {
			runnable.run();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	public static File getFile(String path) throws IOException {
		Path path1 = KubeJS.getGameDirectory().resolve(path);
		KubeJS.verifyFilePath(path1);
		return path1.toFile();
	}
	
	@Nullable
	public static Object copy(@Nullable Object o) {
		if (o instanceof Copyable) {
			return ((Copyable) o).copy();
		} else if (o instanceof JsonElement) {
			return JsonUtilsJS.copy((JsonElement) o);
		} else if (o instanceof Tag) {
			return ((Tag) o).copy();
		}
		
		return o;
	}
	
	@Nullable
	public static Object wrap(@Nullable Object o, JSObjectType type) {
		//Primitives and already normalized objects
		if (o == null || o instanceof WrappedJS || o instanceof Number || o instanceof Character || o instanceof String || o instanceof Enum || o.getClass().isPrimitive() && !o.getClass().isArray()) {
			return o;
		} else if (o instanceof CharSequence || o instanceof Identifier) {
			return o.toString();
		}
		// Vanilla text component
		else if (o instanceof Text) {
			Text t = new TextString("");
			
			List<net.minecraft.text.Text> list = new ArrayList<>();
			list.add((net.minecraft.text.Text) o);
			list.addAll(((net.minecraft.text.Text) o).getSiblings());
			
			for (net.minecraft.text.Text c : list) {
				Text t1;
				
				if (c instanceof TranslatableText) {
					t1 = new TextTranslate(((TranslatableText) c).getKey(), ((TranslatableText) c).getArgs());
				} else {
					t1 = new TextString(c.getString());
				}
				
				//TODO: Replace with AT
				t1.bold(c.getStyle().isBold());
				t1.italic(c.getStyle().isItalic());
				t1.underlined(c.getStyle().isUnderlined());
				t1.strikethrough(c.getStyle().isStrikethrough());
				t1.obfuscated(c.getStyle().isObfuscated());
				t1.insertion(c.getStyle().getInsertion());
				
				ClickEvent ce = c.getStyle().getClickEvent();
				
				if (ce != null) {
					if (ce.getAction() == ClickEvent.Action.RUN_COMMAND) {
						t1.click("command:" + ce.getValue());
					} else if (ce.getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
						t1.click("suggest_command:" + ce.getValue());
					} else if (ce.getAction() == ClickEvent.Action.COPY_TO_CLIPBOARD) {
						t1.click("copy:" + ce.getValue());
					} else if (ce.getAction() == ClickEvent.Action.OPEN_URL) {
						t1.click(ce.getValue());
					}
				}
				
				HoverEvent he = c.getStyle().getHoverEvent();
				
				if (he != null && he.getAction() == HoverEvent.Action.SHOW_TEXT) {
					t1.hover(Text.of(he.getValue(HoverEvent.Action.SHOW_TEXT)));
				}
				
				t.append(t1);
			}
			
			return t;
		}
		// New Nashorn JS Object
		else if (o instanceof JSObject) {
			JSObject js = (JSObject) o;
			
			if (js.isFunction()) {
				return js;
			} else if (js.isArray()) {
				if (!type.checkList()) {
					return null;
				}
				
				ListJS list = new ListJS(js.values().size());
				list.addAll(js.values());
				return list;
			} else if (type.checkMap()) {
				MapJS map = new MapJS();
				
				for (String k : ((JSObject) o).keySet()) {
					map.put(k, ((JSObject) o).getMember(k));
				}
				
				return map;
			} else {
				return null;
			}
		}
		// Old Nashorn JS Object
		else if (o instanceof ScriptObject) {
			ScriptObject js = (ScriptObject) o;
			
			if (js instanceof ScriptFunction) {
				return js;
			} else if (js.isArray()) {
				if (!type.checkList()) {
					return null;
				}
				
				ListJS list = new ListJS(js.size());
				list.addAll(js.values());
				return list;
			} else if (type.checkMap()) {
				MapJS map = new MapJS(js.size());
				
				for (Object k : js.keySet()) {
					map.put(k.toString(), js.get(k));
				}
				
				return map;
			} else {
				return null;
			}
		}
		// Maps
		else if (o instanceof Map) {
			if (!type.checkMap()) {
				return null;
			}
			
			MapJS map = new MapJS(((Map) o).size());
			map.putAll((Map) o);
			return map;
		}
		// Lists, Collections, Iterables, GSON Arrays
		else if (o instanceof Iterable) {
			if (!type.checkList()) {
				return null;
			}
			
			ListJS list = new ListJS();
			
			for (Object o1 : (Iterable) o) {
				list.add(o1);
			}
			
			return list;
		}
		// Arrays (and primitive arrays are a pain)
		else if (o.getClass().isArray()) {
			if (type.checkList()) {
				return ListJS.ofArray(o);
			} else {
				return null;
			}
		}
		// GSON Primitives
		else if (o instanceof JsonPrimitive) {
			return JsonUtilsJS.toPrimitive((JsonPrimitive) o);
		}
		// GSON Objects
		else if (o instanceof JsonObject) {
			if (!type.checkMap()) {
				return null;
			}
			
			MapJS map = new MapJS(((JsonObject) o).size());
			
			for (Map.Entry<String, JsonElement> entry : ((JsonObject) o).entrySet()) {
				map.put(entry.getKey(), entry.getValue());
			}
			
			return map;
		}
		// GSON and NBT Null
		else if (o instanceof JsonNull || o instanceof EndTag) {
			return null;
		}
		// NBT
		else if (o instanceof CompoundTag) {
			if (!type.checkMap()) {
				return null;
			}
			
			CompoundTag nbt = (CompoundTag) o;
			
			MapJS map = new MapJS(nbt.getSize());
			
			for (String s : nbt.getKeys()) {
				map.put(s, nbt.get(s));
			}
			
			return map;
		} else if (o instanceof AbstractNumberTag) {
			return ((AbstractNumberTag) o).getNumber();
		} else if (o instanceof StringTag) {
			return ((StringTag) o).asString();
		}
		
		return o;
	}

//	public static <T> FieldJS<T> getField(String className, String fieldName)
//	{
//		try
//		{
//			return getField(Class.forName(className), fieldName);
//		}
//		catch (Throwable ex)
//		{
//			return new FieldJS<>(null);
//		}
//	}
//
//	@SuppressWarnings({"unchecked", "rawtypes"})
//	public static <T> FieldJS<T> getField(Class className, String fieldName)
//	{
//		try
//		{
//			return new FieldJS<>(ObfuscationReflectionHelper.findField(className, fieldName));
//		}
//		catch (Throwable ex)
//		{
//			return new FieldJS<>(null);
//		}
//	}
	
	public static int parseInt(@Nullable Object object, int def) {
		if (object == null) {
			return def;
		} else if (object instanceof Number) {
			return ((Number) object).intValue();
		}
		
		try {
			String s = object.toString();
			
			if (s.isEmpty()) {
				return def;
			}
			
			return Integer.parseInt(s);
		} catch (Exception ex) {
			return def;
		}
	}
	
	public static double parseDouble(@Nullable Object object, double def) {
		if (object == null) {
			return def;
		} else if (object instanceof Number) {
			return ((Number) object).doubleValue();
		}
		
		try {
			String s = object.toString();
			
			if (s.isEmpty()) {
				return def;
			}
			
			return Double.parseDouble(String.valueOf(object));
		} catch (Exception ex) {
			return def;
		}
	}
	
	public static Stat<Identifier> getStat(@ID String id) {
		return Stats.CUSTOM.getOrCreateStat(getMCID(id));
	}
	
	public static Identifier getToolType(String id) {
		return Identifier.tryParse(id);
	}
	
	public static WorldJS getWorld(World world) {
		if (world.isClient()) {
			return getClientWorld();
		} else {
			return ServerJS.instance.getWorld(world);
		}
	}
	
	public static WorldJS getClientWorld() {
		return KubeJS.instance.proxy.getClientWorld();
	}
	
	@Nullable
	public static StatusEffect getPotion(@ID String id) {
		return Registry.STATUS_EFFECT.get(getMCID(id));
	}
	
	@ID
	public static String getID(@ID @Nullable String s) {
		if (s == null || s.isEmpty()) {
			return "minecraft:air";
		}
		
		if (s.indexOf(':') == -1) {
			return "minecraft:" + s;
		}
		
		return s;
	}
	
	public static Identifier getMCID(@ID @Nullable String s) {
		if (s == null || s.isEmpty()) {
			return new Identifier("minecraft:air");
		}
		
		return new Identifier(s);
	}
	
	public static String getNamespace(@ID @Nullable String s) {
		if (s == null || s.isEmpty()) {
			return "minecraft";
		}
		
		int i = s.indexOf(':');
		return i == -1 ? "minecraft" : s.substring(0, i);
	}
	
	public static String getPath(@ID @Nullable String s) {
		if (s == null || s.isEmpty()) {
			return "air";
		}
		
		int i = s.indexOf(':');
		return i == -1 ? s : s.substring(i + 1);
	}
	
	public static <T> Function<Identifier, Optional<T>> valueGetter(Registry<T> registry, @Nullable T def) {
		return id -> {
			T value = registry.get(id);
			
			if (value != null && value != def) {
				return Optional.of(value);
			}
			
			return Optional.empty();
		};
	}
}