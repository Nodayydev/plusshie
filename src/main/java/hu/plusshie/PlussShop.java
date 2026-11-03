package hu.plusshie;

import net.milkbowl.vault.economy.Economy;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlussShop extends JavaPlugin implements Listener {
    private static final String TITLE = ChatColor.GOLD + "" + ChatColor.BOLD + "Plüssbolt";
    private static final String CATEGORY_TITLE = ChatColor.DARK_GRAY + "Kategória: ";
    private static final int BACK_SLOT = 49;
    private static final int MAX_PRODUCT_SLOTS = 45;
    private static final Pattern PROFILE_NAME = Pattern.compile(
            "(?:minecraft:)?profile\\s*=\\s*\\\"([A-Za-z0-9_]{1,16})\\\"");
    private static final Pattern PROFILE_OBJECT_NAME = Pattern.compile(
            "(?:minecraft:)?profile\\s*=\\s*\\{[^}]*name\\s*:\\s*\\\"([A-Za-z0-9_]{1,16})\\\"");
    private static final Pattern TEXTURES_PROPERTY = Pattern.compile(
            "name\\s*:\\s*\\\"textures\\\"\\s*,\\s*value\\s*:\\s*\\\"([A-Za-z0-9+/=_-]+)\\\"(?:\\s*,\\s*signature\\s*:\\s*\\\"([^\\\"]+)\\\")?");
    private static final Pattern GIVE_ITEM = Pattern.compile(
            "(?i)(?:minecraft:)?give\\s+@p\\s+(?:minecraft:)?([a-z0-9_]+)");

    private Economy economy;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupEconomy();
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault nem található. A shop megnyílik, de vásárolni nem lehet.");
            return;
        }
        RegisteredServiceProvider<Economy> registration =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (registration != null) {
            economy = registration.getProvider();
        } else {
            getLogger().warning("Nem található Vault pénzszolgáltató. Ellenőrizd a gazdasági plugint.");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Ezt a parancsot csak játékos használhatja.");
            return true;
        }
        openCategoryMenu(player);
        return true;
    }

    private List<Category> getCategories() {
        ConfigurationSection section = getConfig().getConfigurationSection("categories");
        List<Category> categories = new ArrayList<>();
        if (section == null) {
            return categories;
        }
        boolean hasAllCategory = section.contains("all");
        for (String key : section.getKeys(false)) {
            if (key.equals("alphabet") && hasAllCategory) {
                continue;
            }
            String path = "categories." + key;
            String categoryKey = key.equals("alphabet") ? "all" : key;
            String name = categoryKey.equals("all")
                    ? "Minden" : getConfig().getString(path + ".name", key);
            Material icon = Material.matchMaterial(getConfig().getString(path + ".icon", "CHEST"));
            String iconCommand = getConfig().getString(path + ".icon-command", "");
            categories.add(new Category(categoryKey, name, icon == null ? Material.CHEST : icon, iconCommand));
        }
        return categories;
    }

    private List<Product> getProducts(Category category) {
        if (category.key.equals("all")) {
            List<Product> allProducts = new ArrayList<>();
            for (Category other : getCategories()) {
                if (!other.key.equals("all")) {
                    allProducts.addAll(getProducts(other));
                }
            }
            return allProducts;
        }

        String path = "categories." + category.key + ".products";
        ConfigurationSection section = getConfig().getConfigurationSection(path);
        List<Product> products = new ArrayList<>();
        if (section == null) {
            return products;
        }
        for (String key : section.getKeys(false)) {
            String productPath = path + "." + key;
            String name = getConfig().getString(productPath + ".name", key);
            double price = getConfig().getDouble(productPath + ".price", 0);
            String command = getConfig().getString(productPath + ".command", "");
            products.add(new Product(key, category.key, category.name, name, price, command));
        }
        return products;
    }

    private void openCategoryMenu(Player player) {
        List<Category> categories = getCategories();
        if (categories.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Nincs beállítva shop kategória.");
            return;
        }
        if (categories.size() > 18) {
            player.sendMessage(ChatColor.RED + "Egy menüben legfeljebb 18 kategória fér el.");
            return;
        }

        Inventory menu = Bukkit.createInventory(null, 36, TITLE);
        ItemStack filler = createItem(Material.WHITE_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < menu.getSize(); slot++) {
            menu.setItem(slot, filler);
        }

        java.util.Map<String, Integer> categorySlots = getCategorySlots(categories);
        for (int index = 0; index < categories.size(); index++) {
            Category category = categories.get(index);
            int slot = categorySlots.get(category.key);
            ItemStack categoryIcon = createCommandIcon(category.iconCommand, category.icon,
                    ChatColor.GREEN + category.name,
                    new String[]{ChatColor.GRAY + "Kattints a kategória megnyitásához"}, menu, player, slot);
            menu.setItem(slot, categoryIcon);
        }
        player.openInventory(menu);
    }

    private java.util.Map<String, Integer> getCategorySlots(List<Category> categories) {
        java.util.Map<String, Integer> slots = new java.util.LinkedHashMap<>();
        List<Category> regularCategories = categories.stream()
                .filter(category -> !category.key.equals("all")).toList();
        int firstRowCount = regularCategories.size() <= 9
                ? regularCategories.size() : (regularCategories.size() + 1) / 2;
        int secondRowCount = regularCategories.size() - firstRowCount;
        int firstColumn = (9 - firstRowCount) / 2;
        for (int i = 0; i < firstRowCount; i++) {
            slots.put(regularCategories.get(i).key, 9 + firstColumn + i);
        }
        if (secondRowCount > 0) {
            int secondColumn = (9 - secondRowCount) / 2;
            for (int i = 0; i < secondRowCount; i++) {
                slots.put(regularCategories.get(firstRowCount + i).key, 18 + secondColumn + i);
            }
        }
        categories.stream().filter(category -> category.key.equals("all"))
                .findFirst().ifPresent(category -> slots.put(category.key, 31));
        return slots;
    }

    private void openProducts(Player player, Category category) {
        List<Product> products = getProducts(category);
        if (products.size() > MAX_PRODUCT_SLOTS) {
            player.sendMessage(ChatColor.RED + "Ebben a kategóriában legfeljebb 45 fej állítható be.");
            return;
        }

        Inventory menu = Bukkit.createInventory(null, 54, CATEGORY_TITLE + category.name);
        ItemStack filler = createItem(Material.WHITE_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < menu.getSize(); slot++) {
            menu.setItem(slot, filler);
        }
        for (int slot = 0; slot < products.size(); slot++) {
            Product product = products.get(slot);
            menu.setItem(slot, createProductIcon(product, player, menu, slot));
        }
        menu.setItem(BACK_SLOT, createItem(Material.ARROW, ChatColor.YELLOW + "Vissza a kategóriákhoz"));
        player.openInventory(menu);
    }

    @EventHandler
    public void onShopClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.startsWith(TITLE) && !title.startsWith(CATEGORY_TITLE)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        if (title.startsWith(TITLE)) {
            List<Category> categories = getCategories();
            java.util.Map<String, Integer> slots = getCategorySlots(categories);
            for (Category category : categories) {
                if (slots.get(category.key) == event.getSlot()) {
                    openProducts(player, category);
                    return;
                }
            }
            return;
        }

        if (event.getSlot() == BACK_SLOT) {
            openCategoryMenu(player);
            return;
        }

        String categoryName = ChatColor.stripColor(title.substring(CATEGORY_TITLE.length()));
        Category category = getCategories().stream()
                .filter(candidate -> candidate.name.equals(categoryName))
                .findFirst().orElse(null);
        if (category == null || event.getSlot() < 0 || event.getSlot() >= MAX_PRODUCT_SLOTS) {
            return;
        }
        List<Product> products = getProducts(category);
        if (event.getSlot() < products.size()) {
            purchase(player, products.get(event.getSlot()));
        }
    }

    private void purchase(Player player, Product product) {
        if (economy == null) {
            player.sendMessage(ChatColor.RED + "A pénzrendszer nem érhető el. Szólj egy adminnak.");
            return;
        }

        String giveCommand = product.command.trim();
        if (giveCommand.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Ehhez a fejhez még nincs beállítva Give Code.");
            return;
        }
        if (giveCommand.startsWith("/")) {
            giveCommand = giveCommand.substring(1);
        }
        if (!giveCommand.matches("(?i)(?:minecraft:)?give\\s+@p\\s+.+")) {
            player.sendMessage(ChatColor.RED + "A fej Give Code-ja hibás. Szólj egy adminnak.");
            getLogger().warning("Hibás Give Code: categories." + product.categoryKey
                    + ".products." + product.key + ".command");
            return;
        }

        if (product.price <= 0) {
            player.sendMessage(ChatColor.RED + "Ennek a terméknek nincs beállítva érvényes ára.");
            return;
        }
        if (!economy.has(player, product.price)) {
            player.sendMessage(ChatColor.RED + "Nincs elég pénzed. Ár: " + ChatColor.GOLD
                    + product.price + getCurrencySuffix());
            return;
        }

        var result = economy.withdrawPlayer(player, product.price);
        if (!result.transactionSuccess()) {
            player.sendMessage(ChatColor.RED + "A fizetés nem sikerült.");
            return;
        }

        boolean commandSucceeded = Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                giveCommand.replace("@p", player.getName()));
        if (!commandSucceeded) {
            economy.depositPlayer(player, product.price);
            player.sendMessage(ChatColor.RED + "A fej kiosztása nem sikerült, a pénzt visszakaptad.");
            return;
        }
        player.sendMessage(ChatColor.GREEN + "Megvásároltad: " + product.name + " "
                + ChatColor.GRAY + "(-" + ChatColor.GOLD + product.price + getCurrencySuffix()
                + ChatColor.GRAY + ")");
    }

    private ItemStack createProductIcon(Product product, Player player, Inventory menu, int slot) {
        return createCommandIcon(product.command, Material.PLAYER_HEAD,
                ChatColor.GREEN + product.name,
                new String[]{ChatColor.GRAY + "Ár: " + ChatColor.GOLD + product.price + getCurrencySuffix(),
                        ChatColor.YELLOW + "Kattints a vásárláshoz"}, menu, player, slot);
    }

    private ItemStack createCommandIcon(String command, Material fallbackMaterial, String displayName,
                                        String[] lore, Inventory menu, Player player, int slot) {
        String normalizedCommand = command == null ? "" : command.trim();
        if (normalizedCommand.startsWith("/")) {
            normalizedCommand = normalizedCommand.substring(1);
        }
        Matcher itemMatcher = GIVE_ITEM.matcher(normalizedCommand);
        // Ha a parancs meg van adva, de az item neve nem olvasható ki,
        // legalább fejként jelenjen meg, ne maradjon a kategória alapikonja.
        Material material = normalizedCommand.isEmpty() ? fallbackMaterial : Material.PLAYER_HEAD;
        if (itemMatcher.find()) {
            Material commandMaterial = Material.matchMaterial(itemMatcher.group(1));
            if (commandMaterial != null) {
                material = commandMaterial;
            }
        }
        ItemStack item = createItem(material, displayName, lore);
        if (material != Material.PLAYER_HEAD) {
            return item;
        }

        Matcher textureMatcher = TEXTURES_PROPERTY.matcher(normalizedCommand);
        if (textureMatcher.find()) {
            String textureValue = textureMatcher.group(1);
            String signature = textureMatcher.group(2);
            UUID profileId = UUID.nameUUIDFromBytes(textureValue.getBytes(StandardCharsets.UTF_8));
            PlayerProfile profile = Bukkit.createProfile(profileId);
            profile.setProperty(signature == null
                    ? new ProfileProperty("textures", textureValue)
                    : new ProfileProperty("textures", textureValue, signature));
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            meta.setPlayerProfile(profile);
            item.setItemMeta(meta);
            return item;
        }

        String ownerName = extractProfileName(normalizedCommand);
        if (ownerName == null) {
            return item;
        }

        PlayerProfile profile = Bukkit.createProfile(ownerName);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setPlayerProfile(profile);
        item.setItemMeta(meta);

        profile.update().thenAccept(updatedProfile -> Bukkit.getScheduler().runTask(this, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != menu
                    || !updatedProfile.hasTextures()) {
                return;
            }
            ItemStack current = menu.getItem(slot);
            if (current == null || !(current.getItemMeta() instanceof SkullMeta currentMeta)) {
                return;
            }
            currentMeta.setPlayerProfile(updatedProfile);
            current.setItemMeta(currentMeta);
            menu.setItem(slot, current);
        }));
        return item;
    }

    private String extractProfileName(String command) {
        Matcher matcher = PROFILE_NAME.matcher(command);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = PROFILE_OBJECT_NAME.matcher(command);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String getCurrencySuffix() {
        String suffix = getConfig().getString("currency.suffix", "&2💲");
        return ChatColor.translateAlternateColorCodes('&', suffix);
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    private record Category(String key, String name, Material icon, String iconCommand) {}
    private record Product(String key, String categoryKey, String categoryName,
                           String name, double price, String command) {}
}
