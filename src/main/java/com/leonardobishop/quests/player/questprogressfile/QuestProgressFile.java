package com.leonardobishop.quests.player.questprogressfile;

import com.leonardobishop.quests.quests.Quest;
import com.leonardobishop.quests.quests.Task;
import com.leonardobishop.quests.Quests;
import com.leonardobishop.quests.obj.Messages;
import com.leonardobishop.quests.obj.Options;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class QuestProgressFile {

    private Map<String, QuestProgress> questProgress = new HashMap<>();
    private UUID player;

    public QuestProgressFile(UUID player) {
        this.player = player;
    }

    //TODO change back to quest id to save performance

    public boolean completeQuest(Quest quest) {
        QuestProgress questProgress = getQuestProgress(quest);
        questProgress.setStarted(false);
        questProgress.setCompleted(true);
        questProgress.setCompletedBefore(true);
        questProgress.setCompletionDate(System.currentTimeMillis());
        if (Bukkit.getPlayer(player) != null) {
            Player player = Bukkit.getPlayer(this.player);
            Bukkit.getServer().getScheduler().runTask(Quests.getInstance(), () -> {
                for (String s : quest.getRewards()) {
                    Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), s.replace("{player}", player.getName()));
                }
            });
            player.sendMessage(Messages.QUEST_COMPLETE.getMessage().replace("{quest}", quest.getDisplayNameStripped()));
            if (Options.TITLES_ENABLED.getBooleanValue()) {
                Quests.getTitle().sendTitle(player, Messages.TITLE_QUEST_COMPLETE_TITLE.getMessage().replace("{quest}", quest
                        .getDisplayNameStripped()), Messages.TITLE_QUEST_COMPLETE_SUBTITLE.getMessage().replace("{quest}", quest
                        .getDisplayNameStripped()));
            }
            for (String s : quest.getRewardString()) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', s));
            }
        }
        return true;
    }

    /**
     * Start a quest for the player.
     *
     * @param quest the quest to check
     * @return 0 if successful, 1 if limit reached, 2 if quest is already completed, 3 if quest has cooldown, 4 if still locked, 5 if already started
     */
    public int startQuest(Quest quest) {
        Player p = Bukkit.getPlayer(player);
        if (getStartedQuests().size() >= Options.QUESTS_START_LIMIT.getIntValue()) {
            if (player != null) {
                p.sendMessage(Messages.QUEST_START_LIMIT.getMessage().replace("{limit}", String.valueOf(Options.QUESTS_START_LIMIT.getIntValue())));
            }
            return 1;
        }
        QuestProgress questProgress = getQuestProgress(quest);
        if (!quest.isRepeatable() && questProgress.isCompletedBefore()) {
            if (player != null) {
                p.sendMessage(Messages.QUEST_START_DISABLED.getMessage());
            }
            return 2;
        }
        long cooldown = getCooldownFor(quest);
        if (cooldown > 0) {
            if (player != null) {
                p.sendMessage(Messages.QUEST_START_COOLDOWN.getMessage().replace("{time}", String.valueOf(Quests.convertToFormat(TimeUnit.MINUTES.convert(cooldown, TimeUnit.MILLISECONDS)))));
            }
            return 3;
        }
        if (!hasMetRequirements(quest)) {
            if (player != null) {
                p.sendMessage(Messages.QUEST_START_LOCKED.getMessage());
            }
            return 4;
        }
        if (questProgress.isStarted()) {
            if (player != null) {
                p.sendMessage(Messages.QUEST_START_STARTED.getMessage());
            }
            return 5;
        }
        questProgress.setStarted(true);
        for (TaskProgress taskProgress : questProgress.getTaskProgress()) {
            taskProgress.setCompleted(false);
            taskProgress.setProgress(null);
        }
        questProgress.setCompleted(false);
        if (Bukkit.getPlayer(player) != null) {
            Player player = Bukkit.getPlayer(getPlayer());
            player.sendMessage(Messages.QUEST_START.getMessage().replace("{quest}", quest.getDisplayNameStripped()));
            if (Options.TITLES_ENABLED.getBooleanValue()) {
                Quests.getTitle().sendTitle(player, Messages.TITLE_QUEST_START_TITLE.getMessage().replace("{quest}", quest
                        .getDisplayNameStripped()), Messages.TITLE_QUEST_START_SUBTITLE.getMessage().replace("{quest}", quest
                        .getDisplayNameStripped()));
            }
        }
        return 0;
    }

    public boolean cancelQuest(Quest quest) {
        QuestProgress questProgress = getQuestProgress(quest);
        if (!questProgress.isStarted()) {
            if (Bukkit.getPlayer(player) != null) {
                Bukkit.getPlayer(getPlayer()).sendMessage(Messages.QUEST_CANCEL_NOTSTARTED.getMessage());
            }
            return false;
        }
        questProgress.setStarted(false);
        for (TaskProgress taskProgress : questProgress.getTaskProgress()) {
            taskProgress.setProgress(null);
        }
        if (Bukkit.getPlayer(player) != null) {
            Bukkit.getPlayer(getPlayer()).sendMessage(Messages.QUEST_CANCEL.getMessage().replace("{quest}", quest.getDisplayNameStripped()));
        }
        return true;
    }

    public void addQuestProgress(QuestProgress questProgress) {
        this.questProgress.put(questProgress.getQuestId(), questProgress);
    }

    public List<Quest> getStartedQuests() {
        List<Quest> startedQuests = new ArrayList<>();
        for (QuestProgress questProgress : questProgress.values()) {
            if (questProgress.isStarted()) {
                startedQuests.add(Quests.getQuestManager().getQuestById(questProgress.getQuestId()));
            }
        }
        return startedQuests;
    }

    public boolean hasQuestProgress(Quest quest) {
        return questProgress.containsKey(quest.getId());
    }

    public boolean hasStartedQuest(Quest quest) {
        //TODO always return true if the need for starting quests is disabled & requirements are met
        if (hasQuestProgress(quest)) {
            if (getQuestProgress(quest).isStarted()) {
                return true;
            }
        }
        return false;
    }

    public long getCooldownFor(Quest quest) {
        QuestProgress questProgress = getQuestProgress(quest);
        if (quest.isCooldownEnabled() && questProgress.isCompleted()) {
            if (questProgress.getCompletionDate() > 0) {
                long date = questProgress.getCompletionDate();
                return (date + TimeUnit.MILLISECONDS.convert(quest.getCooldown(), TimeUnit.MINUTES)) - System.currentTimeMillis();
            }
        }
        return 0;
    }

    public boolean hasMetRequirements(Quest quest) {
        for (String id : quest.getRequirements()) {
            Quest q = Quests.getQuestManager().getQuestById(id);
            if (q == null) {
                continue;
            }
            if (hasQuestProgress(q) && !getQuestProgress(q).isCompletedBefore()) {
                return false;
            } else if (!hasQuestProgress(q)) {
                return false;
            }
        }
        return true;
    }

    public UUID getPlayer() {
        return player;
    }

    public QuestProgress getQuestProgress(Quest quest) {
        if (questProgress.containsKey(quest.getId())) {
            return questProgress.get(quest.getId());
        } else if (generateBlankQuestProgress(quest.getId())) {
            return getQuestProgress(quest);
        }
        return null;
    }

    public boolean generateBlankQuestProgress(String questid) {
        if (Quests.getQuestManager().getQuestById(questid) != null) {
            Quest quest = Quests.getQuestManager().getQuestById(questid);
            QuestProgress questProgress = new QuestProgress(quest.getId(), false, false, 0, player, false, false);
            for (Task task : quest.getTasks()) {
                TaskProgress taskProgress = new TaskProgress(task.getId(), null, player, false);
                questProgress.addTaskProgress(taskProgress);
            }

            addQuestProgress(questProgress);
            return true;
        }
        return false;
    }

    public void saveToDisk() {
        File directory = new File(Quests.getInstance().getDataFolder() + File.separator + "playerdata");
        if (!directory.exists() && !directory.isDirectory()) {
            directory.mkdirs();
        }
        File file = new File(Quests.getInstance().getDataFolder() + File.separator + "playerdata" + File.separator + player.toString() + ".yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        data.set("quest-progress", null);
        for (QuestProgress questProgress : questProgress.values()) {
            if (!questProgress.isWorthSaving()) {
                continue;
            }
            data.set("quest-progress." + questProgress.getQuestId() + ".started", questProgress.isStarted());
            data.set("quest-progress." + questProgress.getQuestId() + ".completed", questProgress.isCompleted());
            data.set("quest-progress." + questProgress.getQuestId() + ".completed-before", questProgress.isCompletedBefore());
            data.set("quest-progress." + questProgress.getQuestId() + ".completion-date", questProgress.getCompletionDate());
            for (TaskProgress taskProgress : questProgress.getTaskProgress()) {
                data.set("quest-progress." + questProgress.getQuestId() + ".task-progress." + taskProgress.getTaskId() + ".completed", taskProgress
                        .isCompleted());
                data.set("quest-progress." + questProgress.getQuestId() + ".task-progress." + taskProgress.getTaskId() + ".progress", taskProgress
                        .getProgress());
            }
        }

        try {
            data.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void clear() {
        questProgress.clear();
    }

}

