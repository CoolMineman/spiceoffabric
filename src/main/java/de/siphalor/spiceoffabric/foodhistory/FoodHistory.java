package de.siphalor.spiceoffabric.foodhistory;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import de.siphalor.spiceoffabric.Config;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class FoodHistory {

    public BiMap<Integer, FoodHistoryEntry> dictionary;
    public int nextId = 0;
    public Queue<Integer> history;
    public Map<Integer, Integer> stats;

    public FoodHistory() {
    	setup();
    }

    public void setup() {
    	dictionary = HashBiMap.create();
    	history = new ConcurrentLinkedQueue<>();
    	stats = new Int2IntArrayMap();
    }

	public CompoundTag write(CompoundTag compoundTag) {
		defragmentDictionary();
		ListTag list = new ListTag();
		for(BiMap.Entry<Integer, FoodHistoryEntry> entry : dictionary.entrySet()) {
			list.add(entry.getKey(), entry.getValue().write(new CompoundTag()));
		}
		compoundTag.put("dictionary", list);
		ListTag historyList = new ListTag();
		for(Integer id : history) {
			historyList.add(new IntTag(id));
		}
		compoundTag.put("history", historyList);
		return compoundTag;
	}

	public static FoodHistory read(CompoundTag compoundTag) {
        FoodHistory foodHistory = new FoodHistory();
		ListTag list = (ListTag) compoundTag.getTag("dictionary");
        for(int i = 0; i < list.size(); i++) {
        	foodHistory.dictionary.put(i, new FoodHistoryEntry().read(list.getCompoundTag(i)));
        }
        foodHistory.nextId = foodHistory.dictionary.size();
        list = (ListTag) compoundTag.getTag("history");
        for(Tag tag : list) {
        	foodHistory.history.offer(((IntTag) tag).getInt());
        }
        foodHistory.buildStats();
		return foodHistory;
	}

	public void buildStats() {
    	stats.clear();
    	for(Integer id : history) {
    		if(stats.containsKey(id))
    			stats.put(id, stats.get(id) + 1);
    		else
				stats.put(id, 1);
	    }
	}

	public void defragmentDictionary() {
		Map<Integer, Integer> oldToNewMap = new HashMap<>();
		int i = 0;
        for(Integer id : dictionary.keySet()) {
        	oldToNewMap.put(id, i);
        	i++;
        }
        nextId = i;
        Queue<Integer> newHistory = new ConcurrentLinkedQueue<>();
        while(true) {
        	Integer id = history.poll();
        	if(id == null) break;
        	newHistory.offer(id);
        }
        history = newHistory;
        Map<Integer, Integer> newStats = new Int2IntArrayMap();
        for(Map.Entry<Integer, Integer> entry : stats.entrySet()) {
        	newStats.put(oldToNewMap.get(entry.getKey()), entry.getValue());
        }
        stats = newStats;
        BiMap<Integer, FoodHistoryEntry> newDictionary = HashBiMap.create();
        for(HashBiMap.Entry<Integer, FoodHistoryEntry> entry : dictionary.entrySet()) {
        	newDictionary.put(oldToNewMap.get(entry.getKey()), entry.getValue());
        }
        dictionary = newDictionary;
	}

	public int getTimesEaten(ItemStack stack) {
    	return stats.getOrDefault(dictionary.inverse().get(FoodHistoryEntry.fromItemStack(stack)), 0);
	}

	public float getFoodSaturationPercentage(ItemStack stack) {
		return 1F - (float) stats.getOrDefault(dictionary.inverse().get(FoodHistoryEntry.fromItemStack(stack)), 0) / 3F;
	}

    public void addFood(ItemStack stack) {
    	FoodHistoryEntry entry = FoodHistoryEntry.fromItemStack(stack);
        Integer id = dictionary.inverse().get(entry);
        if(id == null) {
        	id = nextId++;
        	dictionary.put(id, entry);
        }
        history.offer(id);
        if(history.size() > Config.historyLength.value) {
            removeLastFood();
        }
        stats.put(id, stats.getOrDefault(id, 0) + 1);
    }

    public void removeLastFood() {
    	int id = history.remove();
    	stats.put(id, stats.get(id) - 1);
    }
}