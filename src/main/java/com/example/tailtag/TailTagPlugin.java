package com.example.tailtag;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.ChatColor;
import org.bukkit.potion.PotionEffect;

import java.util.*;

public class TailTagPlugin extends JavaPlugin implements Listener {
    
    private Map<UUID, TeamColor> playerColors = new HashMap<>();
    private Map<UUID, UUID> slaves = new HashMap<>(); // 노예 UUID -> 주인 UUID
    private Map<UUID, Set<UUID>> masters = new HashMap<>(); // 주인 UUID -> 노예들 Set
    private Map<UUID, Long> deadPlayers = new HashMap<>(); // 자연사한 플레이어와 사망 시간
    private Map<UUID, Integer> frozenPlayers = new HashMap<>(); // 움직일 수 없는 플레이어
    private boolean gameActive = false;
    private Location gameCenter;
    private final int GAME_AREA_SIZE = 20; // 20청크
    private BukkitTask gameTask;
    private BukkitTask heartbeatTask;
    
    public enum TeamColor {
        RED(ChatColor.RED, "빨강"),
        ORANGE(ChatColor.GOLD, "주황"),
        YELLOW(ChatColor.YELLOW, "노랑"),
        GREEN(ChatColor.GREEN, "초록"),
        BLUE(ChatColor.BLUE, "파랑"),
        INDIGO(ChatColor.DARK_BLUE, "남색"),
        PURPLE(ChatColor.DARK_PURPLE, "보라"),
        PINK(ChatColor.LIGHT_PURPLE, "핑크"),
        GRAY(ChatColor.GRAY, "회색"),
        BLACK(ChatColor.BLACK, "검정");
        
        private final ChatColor chatColor;
        private final String displayName;
        
        TeamColor(ChatColor chatColor, String displayName) {
            this.chatColor = chatColor;
            this.displayName = displayName;
        }
        
        public ChatColor getChatColor() {
            return chatColor;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("꼬리잡기 플러그인이 활성화되었습니다!");
        
        // 게임 상태 체크 태스크
        startGameTasks();
    }
    
    @Override
    public void onDisable() {
        if (gameTask != null) {
            gameTask.cancel();
        }
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
        }
        getLogger().info("꼬리잡기 플러그인이 비활성화되었습니다!");
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("tailtag")) {
            return false;
        }
        
        if (!(sender instanceof Player)) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "사용법: /tailtag <start|reset>");
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "start":
                startGame(player);
                break;
            case "reset":
                resetGame(player);
                break;
            default:
                player.sendMessage(ChatColor.RED + "사용법: /tailtag <start|reset>");
                break;
        }
        
        return true;
    }
    
    private void startGame(Player commander) {
        if (gameActive) {
            commander.sendMessage(ChatColor.RED + "게임이 이미 진행 중입니다.");
            return;
        }
        
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        if (onlinePlayers.size() < 2) {
            commander.sendMessage(ChatColor.RED + "최소 2명의 플레이어가 필요합니다.");
            return;
        }
        
        if (onlinePlayers.size() > 10) {
            commander.sendMessage(ChatColor.RED + "최대 10명까지만 게임에 참여할 수 있습니다.");
            return;
        }
        
        gameActive = true;
        gameCenter = commander.getLocation();
        
        // 플레이어 색깔 배정
        assignColors(new ArrayList<>(onlinePlayers));
        
        // 플레이어 스폰
        spawnPlayers(new ArrayList<>(onlinePlayers));
        
        // 게임 시작 안내
        for (Player player : onlinePlayers) {
            TeamColor color = playerColors.get(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "게임이 시작되었습니다!");
            player.sendMessage(color.getChatColor() + "당신의 색깔은 " + color.getDisplayName() + "입니다.");
            
            TeamColor targetColor = getTargetColor(color, onlinePlayers.size());
            if (targetColor != null) {
                player.sendMessage(ChatColor.YELLOW + "잡아야 할 색깔: " + targetColor.getChatColor() + targetColor.getDisplayName());
            }
            
            // 인벤토리 저장
            saveInventory(player);
            
           
        }
        
        commander.sendMessage(ChatColor.GREEN + "게임이 시작되었습니다!");
    }
    
    private void resetGame(Player commander) {
        gameActive = false;
        
        // 모든 플레이어 상태 초기화
        for (Player player : Bukkit.getOnlinePlayers()) {
            restoreInventory(player);
            player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            player.clearActivePotionEffects();
            player.setFireTicks(0);
            player.setFoodLevel(20);
            player.setSaturation(20);
        }
        
        // 데이터 초기화
        playerColors.clear();
        slaves.clear();
        masters.clear();
        deadPlayers.clear();
        frozenPlayers.clear();
        
        if (gameTask != null) {
            gameTask.cancel();
        }
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
        }
        
        startGameTasks();
        
        commander.sendMessage(ChatColor.GREEN + "게임이 리셋되었습니다.");
    }
    
    private void assignColors(List<Player> players) {
        Collections.shuffle(players);
        TeamColor[] colors = TeamColor.values();
        
        for (int i = 0; i < players.size(); i++) {
            TeamColor color = colors[i % players.size()];
            playerColors.put(players.get(i).getUniqueId(), color);
            masters.put(players.get(i).getUniqueId(), new HashSet<>());
        }
    }
    
    private void spawnPlayers(List<Player> players) {
        Random random = new Random();
        World world = gameCenter.getWorld();
        
        for (Player player : players) {
            // 20청크 범위 내 랜덤 위치 생성
            int offsetX = (random.nextInt(GAME_AREA_SIZE * 2) - GAME_AREA_SIZE) * 16;
            int offsetZ = (random.nextInt(GAME_AREA_SIZE * 2) - GAME_AREA_SIZE) * 16;
            
            // 안전한 스폰 위치 찾기
            Location spawnLocation = findSafeSpawnLocation(world, 
                gameCenter.getBlockX() + offsetX, 
                gameCenter.getBlockZ() + offsetZ);
            
            player.teleport(spawnLocation);
        }
    }
    
    private Location findSafeSpawnLocation(World world, int x, int z) {
    // 최고 높이부터 시작해서 안전한 위치를 찾음
    int highestY = world.getHighestBlockYAt(x, z);
    
    // 하늘에서 스폰되는 것을 방지하기 위해 최대 높이 제한
    if (highestY > 100) {
        highestY = 100;
    }
    
    // 위에서부터 아래로 내려가면서 안전한 위치 찾기
    for (int y = highestY; y > 0; y--) {
        Location checkLoc = new Location(world, x, y, z);
        
        // 현재 블럭이 고체이고, 위 2블럭이 공기인지 확인
        if (checkLoc.getBlock().getType().isSolid() && 
            !checkLoc.getBlock().getType().equals(Material.LAVA) &&
            !checkLoc.getBlock().getType().equals(Material.WATER) &&
            checkLoc.clone().add(0, 1, 0).getBlock().getType().equals(Material.AIR) &&
            checkLoc.clone().add(0, 2, 0).getBlock().getType().equals(Material.AIR)) {
            
            return checkLoc.clone().add(0.5, 1, 0.5); // 블럭 중앙, 1블럭 위
        }
    }

    // 조건에 맞는 위치가 없을 경우, 기본 위치 사용 (예: Y=64)
    return new Location(world, x + 0.5, 65, z + 0.5);
}
    
    private TeamColor getTargetColor(TeamColor currentColor, int totalPlayers) {
        TeamColor[] colors = Arrays.copyOf(TeamColor.values(), totalPlayers);
        
        for (int i = 0; i < colors.length; i++) {
            if (colors[i] == currentColor) {
                return colors[(i + 1) % colors.length];
            }
        }
        return null;
    }
    
    private void saveInventory(Player player) {
        // 인벤토리 완전 초기화
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        
        // 상태 초기화
        player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.setFireTicks(0);
        player.clearActivePotionEffects();
        player.setExp(0);
        player.setLevel(0);
    }
    
    private void restoreInventory(Player player) {
        // 게임 종료 후 상태 초기화
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        
        // 체력과 상태 복원
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.setFireTicks(0);
        player.clearActivePotionEffects();
        player.setExp(0);
        player.setLevel(0);
    }
    
    private void startGameTasks() {
        // 게임 상태 체크 태스크 (1초마다)
        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameActive) return;
                
                checkGameEnd();
                checkSlaveDistance();
                checkDeadPlayers();
                checkFrozenPlayers();
                updateDragonEggEffects();
                updateSlaveEffects();
            }
        }.runTaskTimer(this, 20L, 20L);
        
        // 하트비트 표시 태스크 (1초마다)
        heartbeatTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameActive) return;
                showHeartbeat();
            }
        }.runTaskTimer(this, 20L, 20L);
    }
    
    private void checkGameEnd() {
        if (!gameActive) return;
        
        Set<UUID> activeMasters = new HashSet<>();
        for (Map.Entry<UUID, Set<UUID>> entry : masters.entrySet()) {
            UUID masterUUID = entry.getKey();
            Player master = Bukkit.getPlayer(masterUUID);
            
            if (master != null && master.isOnline() && !slaves.containsKey(masterUUID)) {
                activeMasters.add(masterUUID);
            }
        }
        
        if (activeMasters.size() == 1) {
            UUID winnerUUID = activeMasters.iterator().next();
            Player winner = Bukkit.getPlayer(winnerUUID);
            
            if (winner != null) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendTitle(ChatColor.GOLD + winner.getName() + "님이 승리하셨습니다!", 
                                   "", 10, 70, 20);
                }
                
                // 게임 종료 후 3초 뒤 리셋
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        resetGame(winner);
                    }
                }.runTaskLater(this, 60L);
            }
        }
    }
    
    private void checkSlaveDistance() {
        for (Map.Entry<UUID, UUID> entry : slaves.entrySet()) {
            UUID slaveUUID = entry.getKey();
            UUID masterUUID = entry.getValue();
            
            Player slave = Bukkit.getPlayer(slaveUUID);
            Player master = Bukkit.getPlayer(masterUUID);
            
            if (slave != null && master != null && slave.isOnline() && master.isOnline()) {
                double distance = slave.getLocation().distance(master.getLocation());
                
                if (distance > 30) {
                    // 노예에게 데미지
                    slave.damage(1.0); // 0.5 하트 데미지
                    
                    if (slave.getHealth() <= 0) {
                        slave.teleport(master.getLocation());
                        slave.setHealth(slave.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
                    }
                }
            }
        }
    }
    
  private void checkDeadPlayers() {
    Iterator<Map.Entry<UUID, Long>> iterator = deadPlayers.entrySet().iterator();
    
    while (iterator.hasNext()) {
        Map.Entry<UUID, Long> entry = iterator.next();
        UUID playerUUID = entry.getKey();
        long deathTime = entry.getValue();
        
        if (System.currentTimeMillis() - deathTime >= 120000) { // 2분
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                // 모든 포션 효과 제거
                for (PotionEffect effect : player.getActivePotionEffects()) {
                    player.removePotionEffect(effect.getType());
                }
                
                // HP를 최대치로 설정
                player.setHealth(player.getMaxHealth());
                
                // 사용자 정의 효과만 적용 (화염 저항 1분)
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 1200, 0));
                
                frozenPlayers.remove(playerUUID);
            }
            iterator.remove();
        }
    }
}

@EventHandler
public void onPlayerDeath(PlayerDeathEvent event) {
    if (!gameActive) return;
    
    Player victim = event.getEntity();
    Player killer = victim.getKiller();
    
    event.setDeathMessage(""); // 킬로그 숨김
    
    if (killer != null && killer instanceof Player) {
        UUID victimUUID = victim.getUniqueId();
        UUID killerUUID = killer.getUniqueId();
        
        TeamColor victimColor = playerColors.get(victimUUID);
        TeamColor killerColor = playerColors.get(killerUUID);
        
        // 실제 주인 찾기 (killer가 노예인 경우 주인을 찾음)
        UUID actualMasterUUID = killerUUID;
        if (slaves.containsKey(killerUUID)) {
            actualMasterUUID = slaves.get(killerUUID);
        }
        Player actualMaster = Bukkit.getPlayer(actualMasterUUID);
        
        // 올바른 색깔 순서로 잡았는지 확인
        TeamColor targetColor = getTargetColor(killerColor, Bukkit.getOnlinePlayers().size());
        
        if (targetColor == victimColor) {
            // 노예로 만들기 (새로운 노예인 경우에만)
            if (!slaves.containsKey(victimUUID)) {
                slaves.put(victimUUID, actualMasterUUID); // 실제 주인의 노예로 만듦
                
                // masters Map 초기화 확인
                if (!masters.containsKey(actualMasterUUID)) {
                    masters.put(actualMasterUUID, new HashSet<>());
                }
                masters.get(actualMasterUUID).add(victimUUID);
                
                // 노예의 색깔을 주인의 색깔로 변경
                TeamColor masterColor = playerColors.get(actualMasterUUID);
                playerColors.put(victimUUID, masterColor);
                
                // 노예 체력 제한 (4칸 = 8.0)
                if (victim.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                    victim.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(8.0);
                    victim.setHealth(8.0);
                }
                
                // 실제 주인의 체력 감소 (새로운 노예를 만들 때만)
                if (actualMaster != null && actualMaster.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                    double currentMaxHealth = actualMaster.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
                    actualMaster.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(Math.max(2.0, currentMaxHealth - 2.0));
                    actualMaster.setHealth(Math.min(actualMaster.getHealth(), actualMaster.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue()));
                }
                
                // 메시지 전송
                victim.sendMessage(ChatColor.RED + actualMaster.getName() + "님의 노예가 되었습니다.");
                if (killer.equals(actualMaster)) {
                    killer.sendMessage(ChatColor.GREEN + victim.getName() + "님이 노예가 되었습니다.");
                } else {
                    killer.sendMessage(ChatColor.GREEN + victim.getName() + "님을 주인을 위해 노예로 만들었습니다.");
                    actualMaster.sendMessage(ChatColor.GREEN + killer.getName() + "님이 " + victim.getName() + "님을 노예로 만들어주었습니다.");
                }
            }
            
            // 노예를 실제 주인 위치로 텔레포트 (항상 실행)
            if (actualMaster != null) {
                victim.teleport(actualMaster.getLocation());
            }
            
        } else if (slaves.containsKey(victimUUID) && slaves.get(victimUUID).equals(killerUUID)) {
            // 노예가 주인에게 죽은 경우 - 불사의 토템 사용
            useTotemOfUndying(victim, true); // 움직임 제한 해제
            
        } else {
            // 주인-노예 관계나 쫓고 쫓기는 관계가 아닌 경우 - 자연사 처리
            handleNaturalDeath(victim);
        }
    } else {
        // 자연사 - 불사의 토템 사용
        useTotemOfUndying(victim, false);
    }
}

// 불사의 토템 사용 메서드
private void useTotemOfUndying(Player player, boolean removeFrozen) {
    UUID playerUUID = player.getUniqueId();
    
    // 불사의 토템 효과 적용
    player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue()); // 풀피로 회복
    
    // 기존 포션 효과 모두 제거 (불사의 토템 버프 제거)
    for (PotionEffect effect : player.getActivePotionEffects()) {
        player.removePotionEffect(effect.getType());
    }
    
    if (removeFrozen) {
        // 노예가 주인에게 죽은 경우 - 움직임 제한 해제
        frozenPlayers.remove(playerUUID);
        player.sendMessage(ChatColor.GREEN + "주인말을 잘 들으십쇼.");
    } else {
        // 자연사의 경우 - 2분간 움직임 제한
        deadPlayers.put(playerUUID, System.currentTimeMillis());
        frozenPlayers.put(playerUUID, 120); // 120초
        player.sendMessage(ChatColor.RED + "2분간 움직일 수 없습니다.");
    }
    
    // 불사의 토템 효과 시각적 표시
    player.getWorld().spawnParticle(Particle.TOTEM, player.getLocation(), 30);
    player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
}

// 자연사 처리 메서드 (기존 코드 유지)
private void handleNaturalDeath(Player victim) {
    UUID victimUUID = victim.getUniqueId();
    deadPlayers.put(victimUUID, System.currentTimeMillis());
    frozenPlayers.put(victimUUID, 120); // 120초
    
    victim.sendMessage(ChatColor.RED + "자연사로 인해 2분간 움직일 수 없습니다.");
}
 private void checkFrozenPlayers() {
        Iterator<Map.Entry<UUID, Integer>> iterator = frozenPlayers.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            UUID playerUUID = entry.getKey();
            int timeLeft = entry.getValue() - 1;
            
            if (timeLeft <= 0) {
                iterator.remove();
            } else {
                frozenPlayers.put(playerUUID, timeLeft);
                
                // 플레이어 이동 제한
                Player player = Bukkit.getPlayer(playerUUID);
                if (player != null && player.isOnline()) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 25, 255, false, false));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 25, -10, false, false));
                }
            }
        }
    }
    
    private void updateDragonEggEffects() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getInventory().contains(Material.DRAGON_EGG)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 25, 1, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 25, 0, false, false));
            }
        }
    }
    
    private void updateSlaveEffects() {
        for (UUID slaveUUID : slaves.keySet()) {
            Player slave = Bukkit.getPlayer(slaveUUID);
            if (slave != null && slave.isOnline()) {
                // 노예에게 나약함 2 효과 지속 부여
                slave.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 25, 1, false, false));
            }
        }
    }
    
    private void showHeartbeat() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!gameActive) continue;
            
            UUID playerUUID = player.getUniqueId();
            
            if (slaves.containsKey(playerUUID)) {
                // 노예인 경우 주인과의 거리 표시
                UUID masterUUID = slaves.get(playerUUID);
                Player master = Bukkit.getPlayer(masterUUID);
                
                if (master != null && master.isOnline()) {
                    double distance = player.getLocation().distance(master.getLocation());
                    player.sendActionBar(ChatColor.YELLOW + "주인과의 거리: " + (int)distance + "블럭");
                }
            } else {
                // 주인인 경우 추적자 감지
                TeamColor playerColor = playerColors.get(playerUUID);
                if (playerColor != null) {
                    TeamColor hunterColor = getHunterColor(playerColor, Bukkit.getOnlinePlayers().size());
                    
                    if (hunterColor != null) {
                        for (Player other : Bukkit.getOnlinePlayers()) {
                            TeamColor otherColor = playerColors.get(other.getUniqueId());
                            
                            if (otherColor == hunterColor && !slaves.containsKey(other.getUniqueId())) {
                                double distance = player.getLocation().distance(other.getLocation());
                                
                                if (distance <= 30) {
                                    player.sendActionBar(ChatColor.RED + "❤");
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private TeamColor getHunterColor(TeamColor currentColor, int totalPlayers) {
        TeamColor[] colors = Arrays.copyOf(TeamColor.values(), totalPlayers);
        
        for (int i = 0; i < colors.length; i++) {
            if (colors[i] == currentColor) {
                return colors[(i - 1 + colors.length) % colors.length];
            }
        }
        return null;
    }
    
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!gameActive) return;
        
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            UUID playerUUID = player.getUniqueId();
            
            // 자연사로 인해 얼어있는 플레이어는 환경 데미지만 무효 (플레이어 공격은 허용)
            if (frozenPlayers.containsKey(playerUUID)) {
                // 플레이어가 공격한 것이 아닌 경우에만 데미지 무효
                if (!(event instanceof EntityDamageByEntityEvent)) {
                    event.setCancelled(true);
                    return;
                } else {
                    EntityDamageByEntityEvent entityEvent = (EntityDamageByEntityEvent) event;
                    // 공격자가 플레이어가 아닌 경우 데미지 무효
                    if (!(entityEvent.getDamager() instanceof Player)) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }
    
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!gameActive) return;
        
        if (event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();
            UUID victimUUID = victim.getUniqueId();
            
            // 자연사로 인해 얼어있는 플레이어도 다른 플레이어에게는 공격받을 수 있음
            // (위의 onEntityDamage에서 이미 처리됨)
        }
        
        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            Player victim = (Player) event.getEntity();
            
            UUID attackerUUID = attacker.getUniqueId();
            UUID victimUUID = victim.getUniqueId();
            
            // 노예가 주인을 공격하려는 경우
            if (slaves.containsKey(attackerUUID)) {
                UUID masterUUID = slaves.get(attackerUUID);
                if (masterUUID.equals(victimUUID)) {
                    event.setCancelled(true);
                    attacker.sendMessage(ChatColor.RED + "하극상은 안됩니다");
                    return;
                }
            }
        }
    }
      
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!gameActive) return;
        
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        
        if (item != null && item.getType() == Material.DIAMOND && 
            (event.getAction().name().contains("RIGHT_CLICK"))) {
            
            UUID playerUUID = player.getUniqueId();
            TeamColor playerColor = playerColors.get(playerUUID);
            
            if (playerColor != null) {
                TeamColor targetColor = getTargetColor(playerColor, Bukkit.getOnlinePlayers().size());
                
                if (targetColor != null) {
                    Player target = findPlayerWithColor(targetColor);
                    
                    if (target != null) {
                        if (!target.getWorld().equals(player.getWorld())) {
                            player.sendMessage(ChatColor.RED + "타겟이 같은 월드에 존재하지 않습니다.");
                            return;
                        }
                        
                        // 다이아 소모
                        item.setAmount(item.getAmount() - 1);
                        
                        // 방향 표시
                        showDirectionToTarget(player, target);
                    }
                }
            }
        }
    }
    
  private Player findPlayerWithColor(TeamColor color) {
    for (Map.Entry<UUID, TeamColor> entry : playerColors.entrySet()) {
        if (entry.getValue() == color) {
            Player player = Bukkit.getPlayer(entry.getKey());
            // slaves.containsKey 조건 제거 - 노예도 타겟으로 찾을 수 있게 함
            if (player != null && player.isOnline()) {
                return player;
            }
        }
    }
    return null;
}
    
    private void showDirectionToTarget(Player player, Player target) {
    Location playerLoc = player.getLocation();
    Location targetLoc = target.getLocation();
    
    // 방향 벡터 계산
    double tempDx = targetLoc.getX() - playerLoc.getX();
    double tempDy = targetLoc.getY() - playerLoc.getY();
    double tempDz = targetLoc.getZ() - playerLoc.getZ();
    
    // 정규화
    double length = Math.sqrt(tempDx*tempDx + tempDy*tempDy + tempDz*tempDz);
    final double dx = tempDx / length;
    final double dy = tempDy / length;
    final double dz = tempDz / length;
    
    // 3초 동안 파티클 표시 (20틱 = 1초)
    final int duration = 60; // 3초 = 60틱
    final int interval = 2; // 2틱마다 실행 (더 부드러운 효과)
    
    BukkitRunnable particleTask = new BukkitRunnable() {
        int ticks = 0;
        
        @Override
        public void run() {
            if (ticks >= duration) {
                this.cancel();
                return;
            }
            
            // 파티클 생성
            for (int i = 1; i <= 8; i++) {
                Location particleLoc = playerLoc.clone().add(dx*i, dy*i + 1.5, dz*i);
                player.getWorld().spawnParticle(Particle.REDSTONE, particleLoc, 1, 
                    new Particle.DustOptions(org.bukkit.Color.RED, 1.5f)); // 크기도 조금 키움
            }
            
            ticks += interval;
        }
    };
    
    // 스케줄러 실행
    particleTask.runTaskTimer(this, 0L, interval); // this는 현재 플러그인 인스턴스
    
    player.sendMessage(ChatColor.YELLOW + "타겟 방향을 3초간 표시합니다!");
}
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (gameActive) {
            event.getPlayer().sendMessage(ChatColor.YELLOW + "게임이 진행 중입니다.");
        }
    }
    
    @EventHandler
    public void onPlayerAdvancement(PlayerAdvancementDoneEvent event) {
        // 발전과제 메시지 숨김
        event.message(null);
    }
}
