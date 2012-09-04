package updaters;

import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import play.Logger;
import play.libs.WS;
import play.libs.WS.HttpResponse;

import models.BikeRentalSystem;
import models.BikeRentalSystemType;
import models.MetroArea;
import models.ReviewType;

public class CityBikesUpdater implements Updater {

    @Override
    public Set<MetroArea> update() {
        HttpResponse res = WS.url("http://api.citybik.es/networks.json").get();
        int status = res.getStatus();
        if (status != 200) {
            Logger.error("Status %s retrieving data from CityBik.es API", status);
            return null;
        }
        
        Set<MetroArea> updated = new HashSet<MetroArea>();
        
        JsonArray json = res.getJson().getAsJsonArray();
        
        for (JsonElement rawSystem : json) {
            boolean isNew = false;
            JsonObject desc = rawSystem.getAsJsonObject();
            
            BikeRentalSystem system = BikeRentalSystem.find("byCityBikesId", desc.get("id").getAsInt()).first();
            
            if (system == null) {
                isNew = true;
                system = new BikeRentalSystem();
            }
            
            if (desc.get("name").getAsString().equals(system.name) &&
                    desc.get("url").getAsString().equals(system.url))
                continue;               
          
            system.name = desc.get("name").getAsString();
            system.url = desc.get("url").getAsString();
                        
            system.cityBikesId = desc.get("id").getAsInt();
            system.type = BikeRentalSystemType.CITYBIKES;
            
            if (isNew) {
                MetroArea metro = MetroArea.findByGeom(desc.get("lat").getAsDouble() / 1000000, 
                        desc.get("lng").getAsDouble() / 1000000);
                if (metro == null) {
                    system.review = ReviewType.NO_METRO;
                }
                else {
                    system.metroArea = metro;
                }
            }
            
            if (system.metroArea != null)
                updated.add(system.metroArea);
            
            system.save();
        }
        
        return updated;
    }
}
