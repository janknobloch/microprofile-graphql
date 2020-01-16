/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.microprofile.graphql.tck.apps.superhero.api;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.json.bind.annotation.JsonbDateFormat;
import javax.json.bind.annotation.JsonbNumberFormat;

import org.eclipse.microprofile.graphql.DefaultValue;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.GraphQLException;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Query;
import org.eclipse.microprofile.graphql.Source;
import org.eclipse.microprofile.graphql.tck.apps.superhero.db.DuplicateSuperHeroException;
import org.eclipse.microprofile.graphql.tck.apps.superhero.db.HeroDatabase;
import org.eclipse.microprofile.graphql.tck.apps.superhero.db.HeroLocator;
import org.eclipse.microprofile.graphql.tck.apps.superhero.db.SidekickDatabase;
import org.eclipse.microprofile.graphql.tck.apps.superhero.db.UnknownHeroException;
import org.eclipse.microprofile.graphql.tck.apps.superhero.db.UnknownSidekickException;
import org.eclipse.microprofile.graphql.tck.apps.superhero.db.UnknownTeamException;
import org.eclipse.microprofile.graphql.tck.apps.superhero.model.Character;
import org.eclipse.microprofile.graphql.tck.apps.superhero.model.Item;
import org.eclipse.microprofile.graphql.tck.apps.superhero.model.Sidekick;
import org.eclipse.microprofile.graphql.tck.apps.superhero.model.SuperHero;
import org.eclipse.microprofile.graphql.tck.apps.superhero.model.Team;
import org.eclipse.microprofile.graphql.tck.apps.superhero.model.UnknownCharacterException;
import org.eclipse.microprofile.graphql.Name;

@GraphQLApi
public class HeroFinder {
    private static final Logger LOG = Logger.getLogger(HeroFinder.class.getName());

    @Inject
    private HeroDatabase heroDB;

    @Inject
    private SidekickDatabase sidekickDB;

    @Inject
    private HeroLocator heroLocator;

    @Query
    public Character character(@Name("name") String name) throws UnknownCharacterException {
        LOG.log(Level.INFO, "character invoked [{0}]", name);

        try {
            SuperHero superHero = heroDB.getHero(name);
            return superHero;
        } catch (UnknownHeroException e) {
            try {
                Sidekick sidekick = sidekickDB.getSidekick(name);
                return sidekick;
            } catch (UnknownSidekickException ex) {
                throw new UnknownCharacterException(name);
            }
        }
    }

    @Query
    public SuperHero superHero(@Name("name") @Description("Super hero name, not real name") String name) throws UnknownHeroException {
        LOG.log(Level.INFO, "superHero invoked [{0}]", name);
        return Optional.ofNullable(heroDB.getHero(name)).orElseThrow(() -> new UnknownHeroException(name));
    }

    @Query @Description("Testing the blacklist of Checked Exceptions")
    public SuperHero exportToFile(@Name("name") @Description("Super hero name, not real name") String name) throws IOException {
        LOG.log(Level.INFO, "exportToFile invoked [{0}]", name);
        throw new IOException("No you can not do this.");
    }

    @Query @Description("Testing the default blacklist for Runtime Exceptions")
    public SuperHero villian(@Name("name") @Description("Super hero name, not real name") String name) {
        LOG.log(Level.INFO, "villian invoked [{0}]", name);
        throw new RuntimeException("SuperHero can not be a villian");
    }

    @Query @Description("Testing the whitelist for Runtime Exceptions")
    public SuperHero weakness(@Name("name") @Description("Super hero name, not real name") String name) {
        LOG.log(Level.INFO, "weakness invoked [{0}]", name);
        throw new WeaknessNotFoundException("Superhero has no weakness");
    }

    @Query @Description("Testing Errors, as in Java Error")
    public SuperHero wreakHavoc(@Name("name") @Description("Super hero name, not real name") String name) {
        LOG.log(Level.INFO, "wreakHavoc invoked [{0}]", name);
        throw new OutOfMemoryError("a SuperHero has used all the memory");
    }

    @Query
    @Description("List all super heroes in the database")
    public Collection<SuperHero> allHeroes() {
        LOG.info("allHeroes invoked");
        return heroDB.getAllHeroes();
    }

    @Query
    public Collection<SuperHero> allHeroesIn(@DefaultValue("New York, NY") @Name("city") String city) {
        LOG.log(Level.INFO, "allHeroesIn invoked [{0}]", city);
        return allHeroesByFilter(hero -> {
            return city.equals(hero.getPrimaryLocation());
        });
    }

    @Query
    public Collection<SuperHero> allHeroesWithPower(@Name("power") String power) {
        LOG.log(Level.INFO, "allHeroesWithPower invoked [{0}]", power);
        return allHeroesByFilter(hero -> {
            return hero.getSuperPowers().contains(power);
        });
    }

    @Query
    public Collection<SuperHero> allHeroesInTeam(@Name("team") String teamName) throws UnknownTeamException {
        LOG.log(Level.INFO, "allHeroesInTeam invoked [{0}]", teamName);
        return heroDB.getTeam(teamName).getMembers();
    }

    @Query
    public Collection<Team> allTeams() {
        LOG.info("allTeams invoked");
        return heroDB.getAllTeams();
    }

    @Mutation
    public SuperHero createNewHero(@Name("hero") SuperHero newHero) throws DuplicateSuperHeroException, UnknownHeroException {
        LOG.log(Level.INFO, "createNewHero invoked [{0}]", newHero);
        heroDB.addHero(newHero);
        return heroDB.getHero(newHero.getName());
    }

    @Mutation
    public Collection<SuperHero> createNewHeroes(@Name("heroes") List<SuperHero> newHeroes) throws DuplicateSuperHeroException, UnknownHeroException {
        LOG.log(Level.INFO, "createNewHeroes invoked [{0}]", newHeroes);
        heroDB.addHeroes(newHeroes);
        return newHeroes;
    }
    
    @Mutation
    public SuperHero[] createNewHeroesWithArray(@Name("heroes") SuperHero[] newHeroes) throws DuplicateSuperHeroException, UnknownHeroException {
        LOG.log(Level.INFO, "createNewHeroesWithArray invoked [{0}]", newHeroes);
        List<SuperHero> asList = Arrays.asList(newHeroes);
        heroDB.addHeroes(asList);
        return newHeroes;
    }
    
    @Mutation
    @Description("Adds a hero to the specified team and returns the updated team.")
    public Team addHeroToTeam(@Name("hero") String heroName,
                              @Name("team") String teamName)
            throws UnknownTeamException, UnknownHeroException {

        LOG.log(Level.INFO, "addHeroToTeam invoked [{0}],[{1}]", new Object[]{heroName, teamName});
        return heroDB.getTeam(teamName)
                .addMembers(heroDB.getHero(heroName));
    }

    @Mutation
    @Description("Removes a hero to the specified team and returns the updated team.")
    public Team removeHeroFromTeam(@Name("hero") String heroName,
                                   @Name("team") String teamName)
            throws UnknownTeamException, UnknownHeroException {
        LOG.log(Level.INFO, "removeHeroFromTeam invoked [{0}],[{1}]", new Object[]{heroName, teamName});
        return heroDB.getTeam(teamName)
                .removeMembers(heroDB.getHero(heroName));
    }

    @Mutation
    @Description("Removes a hero... permanently...")
    public Collection<SuperHero> removeHero(@Name("hero") String heroName) throws UnknownHeroException {
        LOG.log(Level.INFO, "removeHero invoked [{0}]", heroName);
        if (heroDB.removeHero(heroName) == null) {
            throw new UnknownHeroException(heroName);
        }
        return allHeroes();
    }

    @Mutation
    @Description("Gives a hero new equipment")
    public SuperHero provisionHero(@Name("hero") String heroName,
                                   @DefaultValue(Item.CAPE) @Name("item") Item item)
            throws UnknownHeroException {
        LOG.log(Level.INFO, "provisionHero invoked [{0}],[{1}]", new Object[]{heroName, item});
        SuperHero hero = heroDB.getHero(heroName);
        if (hero == null) {
            throw new UnknownHeroException(heroName);
        }
        hero.getEquipment().add(item);
        return hero;
    }

    @Mutation
    @Description("Removes equipment from a hero")
    public SuperHero removeItemFromHero(@Name("hero") String heroName,
                                        @Name("itemID") long itemID)
            throws UnknownHeroException {
        LOG.log(Level.INFO, "removeItemFromHero invoked [{0}],[{1}]", new Object[]{heroName, itemID});
        SuperHero hero = heroDB.getHero(heroName);
        if (hero == null) {
            throw new UnknownHeroException(heroName);
        }
        hero.getEquipment().removeIf(i -> {
            return i.getId() == itemID;
        });
        return hero;
    }
    
    @Mutation
    @Description("Update an item's powerLevel") 
    public Item updateItemPowerLevel(@Name("itemID") long itemID,
                                     @DefaultValue("5") @Name("powerLevel") int newLevel) {
        LOG.log(Level.INFO, "updateItemPowerLevel invoked [{0}],[{1}]", new Object[]{itemID, newLevel});
        Item item = null;
        for (SuperHero hero : allHeroes()) {
            for (Item i : hero.getEquipment()) {
                if (i.getId() == itemID) {
                    item = i;
                    item.setPowerLevel(newLevel);
                }
            }
        }
        return item;
    }
    
    @Mutation
    @Description("Update a hero's back account") 
    public SuperHero updateBankBalance(@Name("name") String name,
                                     @Name("bankBalance") double bankBalance) throws UnknownHeroException {
        LOG.log(Level.INFO, "updateBankBalance invoked [{0}],[{1}]", new Object[]{name, bankBalance});
        SuperHero superHero = heroDB.getHero(name);
        if(superHero!=null){
            superHero.setBankBalance(bankBalance);
        }
        return superHero;
    }
    
    @Mutation
    @Description("Update a hero's back account in South African Rand") 
    public SuperHero updateBankBalanceInZAR(@Name("name") String name,
                                     @JsonbNumberFormat(value = "¤ 000.00",locale = "en_ZA") 
                                     @Name("bankBalance") double bankBalance) throws UnknownHeroException {
        LOG.log(Level.INFO, "updateBankBalance invoked [{0}],[{1}]", new Object[]{name, bankBalance});
        SuperHero superHero = heroDB.getHero(name);
        if(superHero!=null){
            superHero.setBankBalance(bankBalance);
        }
        return superHero;
    }
    
    @Mutation
    @Description("Update a hero's favourite drink size") 
    public SuperHero favouriteDrinkSize(@Name("name") String name,
                                     @Name("size") float size) throws UnknownHeroException {
        LOG.log(Level.INFO, "favouriteDrinkSize invoked [{0}],[{1}]", new Object[]{name, size});
        SuperHero superHero = heroDB.getHero(name);
        if(superHero!=null){
            superHero.setFavouriteDrinkSize(size);
        }
        return superHero;
    }
    
    @Mutation
    @Description("Update a hero's favourite drink size in milliliters") 
    public SuperHero favouriteDrinkSizeInML(@Name("name") String name,
                                     @JsonbNumberFormat(value = "000.00 'ml'") 
                                     @Name("size") Float size) throws UnknownHeroException {
        LOG.log(Level.INFO, "favouriteDrinkSizeInML invoked [{0}],[{1}]", new Object[]{name, size});
        SuperHero superHero = heroDB.getHero(name);
        if(superHero!=null){
            superHero.setFavouriteDrinkSize(size);
        }
        return superHero;
    }
    
    @Mutation
    @Description("Update a hero's net worth") 
    public SuperHero updateNetWorth(@Name("name") String name,
                                     @Name("netWorth") BigDecimal netWorth) throws UnknownHeroException {
        LOG.log(Level.INFO, "updateNetWorth invoked [{0}],[{1}]", new Object[]{name, netWorth});
        SuperHero superHero = heroDB.getHero(name);
        if(superHero!=null){
            superHero.setNetWorth(netWorth);
        }
        return superHero;
    }
    
    @Mutation
    @Description("Update a hero's back account in USD") 
    public SuperHero updateNetWorthInUSD(@Name("name") String name,
                                     @JsonbNumberFormat(value = "¤ 000.00",locale = "en_US") 
                                     @Name("netWorth") BigDecimal netWorth) throws UnknownHeroException {
        LOG.log(Level.INFO, "updateBankBalance invoked [{0}],[{1}]", new Object[]{name, netWorth});
        SuperHero superHero = heroDB.getHero(name);
        if(superHero!=null){
            superHero.setNetWorth(netWorth);
        }
        return superHero;
    }
    
    @Mutation
    @Description("Log the last place the hero was seen") 
    public SuperHero logLocation(@Name("name") String name,
                                     @Name("coordinates") ArrayList<BigDecimal> coordinates) throws UnknownHeroException {
        LOG.log(Level.INFO, "logLocation invoked [{0}],[{1}]", new Object[]{name, coordinates});
        SuperHero superHero = heroDB.getHero(name);
        if(superHero!=null){
            superHero.setLastKnownCoordinates(coordinates);
        }
        return superHero;
    }
    
    @Mutation
    @Description("Log the last place the hero was seen (Long Lat)") 
    public SuperHero logLocationLongLat(@Name("name") String name,
                                     @JsonbNumberFormat("00.0000000 'longlat'") 
                                     @Name("coordinates") List<BigDecimal> coordinates) throws UnknownHeroException {
        LOG.log(Level.INFO, "logLocationLongLat invoked [{0}],[{1}]", new Object[]{name, coordinates});
        SuperHero superHero = heroDB.getHero(name);
        if(superHero!=null){
            superHero.setLastKnownCoordinates(coordinates);
        }
        return superHero;
    }
    
    @Mutation
    @Description("Log the last few places the hero was seen") 
    public SuperHero trackHero(@Name("name") String name,
                                     @Name("coordinates") List<List<BigDecimal>> coordinates) throws UnknownHeroException {
        LOG.log(Level.INFO, "trackHero invoked [{0}],[{1}]", new Object[]{name, coordinates});
        SuperHero superHero = heroDB.getHero(name);
        if(superHero!=null){
            superHero.setTrack(coordinates);
        }
        return superHero;
    }
    
    @Mutation
    @Description("Log the last few places the hero was seen (Long Lat)") 
    public SuperHero trackHeroLongLat(@Name("name") String name,
                                     @JsonbNumberFormat("00.0000000 'longlat'") 
                                     @Name("coordinates") List<List<BigDecimal>> coordinates) throws UnknownHeroException {
        LOG.log(Level.INFO, "trackHeroLongLat invoked [{0}],[{1}]", new Object[]{name, coordinates});
        SuperHero superHero = heroDB.getHero(name);
        if(superHero!=null){
            superHero.setTrack(coordinates);
        }
        return superHero;
    }
    
    @Mutation
    @Description("Log the last place the hero was seen using an array") 
    public SuperHero logLocationWithArray(@Name("name") String name,
                                     @Name("coordinates") BigDecimal[] coordinates) throws UnknownHeroException {
        LOG.log(Level.INFO, "logLocationWithArray invoked [{0}],[{1}]", new Object[]{name, coordinates});
        SuperHero superHero = heroDB.getHero(name);
        if(superHero!=null){
            superHero.setLastKnownCoordinates(Arrays.asList(coordinates));
        }
        return superHero;
    }
    
    @Mutation
    @Description("Log the last place the hero was seen (Long Lat) using an array") 
    public SuperHero logLocationLongLatWithArray(@Name("name") String name,
                                     @JsonbNumberFormat("00.0000000 'longlat'") 
                                     @Name("coordinates") BigDecimal[] coordinates) throws UnknownHeroException {
        LOG.log(Level.INFO, "logLocationLongLatWithArray invoked [{0}],[{1}]", new Object[]{name, coordinates});
        SuperHero superHero = heroDB.getHero(name);
        if(superHero!=null){
            superHero.setLastKnownCoordinates(Arrays.asList(coordinates));
        }
        return superHero;
    }
    
    @Mutation
    @Description("Update an item's powerLevel in persentage") 
    public Item updateItemPowerLevelPersentage(@Name("itemID") long itemID,
                                     @JsonbNumberFormat("##'%'") @Name("powerLevel") int newLevel) {
        LOG.log(Level.INFO, "updateItemPowerLevelPersentage invoked [{0}],[{1}]", new Object[]{itemID, newLevel});
        Item item = null;
        for (SuperHero hero : allHeroes()) {
            for (Item i : hero.getEquipment()) {
                if (i.getId() == itemID) {
                    item = i;
                    item.setPowerLevel(newLevel/20 );
                }
            }
        }
        return item;
    }

    @Mutation
    @Description("Check in a superhero") 
    public SuperHero checkIn(@Name("name") String name,
                             @Name("date") LocalDate localDate) throws UnknownHeroException {
        LOG.log(Level.INFO, "checkIn invoked [{0}],[{1}]", new Object[]{name, localDate});
        SuperHero superHero = heroDB.getHero(name);
        if(superHero!=null){
            superHero.setDateOfLastCheckin(localDate);
        }
        return superHero;
    }
    
    @Mutation
    @Description("Check in a superhero") 
    public SuperHero checkInWithCorrectDateFormat(@Name("name") String name,
                             @JsonbDateFormat("MM/dd/yyyy") @Name("date") LocalDate localDate) throws UnknownHeroException {
        LOG.log(Level.INFO, "checkInWithCorrectDateFormat invoked [{0}],[{1}]", new Object[]{name, localDate});
        SuperHero superHero = heroDB.getHero(name);
        if(superHero!=null){
            superHero.setDateOfLastCheckin(localDate);
        }
        return superHero;
    }
    
    @Mutation
    @Description("Set the time a here started patrolling") 
    public SuperHero startPatrolling(@Name("name") String name,
                             @Name("time") LocalTime localTime) throws UnknownHeroException {
        LOG.log(Level.INFO, "startPatrolling invoked [{0}],[{1}]", new Object[]{name, localTime});
        SuperHero superHero = heroDB.getHero(name);
        if(superHero!=null){
            superHero.setPatrolStartTime(localTime);
        }
        return superHero;
    }
    
    @Mutation
    @Description("Set the time a here started patrolling") 
    public SuperHero startPatrollingWithCorrectDateFormat(@Name("name") String name,
                             @JsonbDateFormat("HH:mm") @Name("time") LocalTime localTime) throws UnknownHeroException {
        LOG.log(Level.INFO, "startPatrollingWithCorrectDateFormat invoked [{0}],[{1}]", new Object[]{name, localTime});
        SuperHero superHero = heroDB.getHero(name);
        if(superHero!=null){
            superHero.setPatrolStartTime(localTime);
        }
        return superHero;
    }
    
    @Mutation
    @Description("Start a battle") 
    public SuperHero battle(@Name("name") String name,
                             @Name("dateTime") LocalDateTime localDateTime) throws UnknownHeroException {
        LOG.log(Level.INFO, "battle invoked [{0}],[{1}]", new Object[]{name, localDateTime});
        SuperHero superHero = heroDB.getHero(name);
        if(superHero!=null){
            superHero.setTimeOfLastBattle(localDateTime);
        }
        return superHero;
    }
    
    @Mutation
    @Description("Start a battle") 
    public SuperHero battleWithCorrectDateFormat(@Name("name") String name,
                             @JsonbDateFormat("HH:mm:ss dd-MM-yyyy") @Name("dateTime") LocalDateTime localDateTime) throws UnknownHeroException {
        LOG.log(Level.INFO, "battleWithCorrectDateFormat invoked [{0}],[{1}]", new Object[]{name, localDateTime});
        SuperHero superHero = heroDB.getHero(name);
        if(superHero!=null){
            superHero.setTimeOfLastBattle(localDateTime);
        }
        return superHero;
    }
    
    @Mutation
    @Description("Set the ID Number for a hero") 
    public SuperHero idNumber(@Name("name") String name,
                             @Name("id") Long idNumber) throws UnknownHeroException {
        LOG.log(Level.INFO, "idNumber invoked [{0}],[{1}]", new Object[]{name, idNumber});
        SuperHero superHero = heroDB.getHero(name);
        if(superHero!=null){
            superHero.setIdNumber(idNumber);
        }
        return superHero;
    }
    
    @Mutation
    @Description("Set the ID Number for a hero") 
    public SuperHero idNumberWithCorrectFormat(@Name("name") String name,
                             @JsonbNumberFormat("0000,0000") @Name("id") Long idNumber) throws UnknownHeroException {
        LOG.log(Level.INFO, "idNumberWithCorrectFormat invoked [{0}],[{1}]", new Object[]{name, idNumber});
        SuperHero superHero = heroDB.getHero(name);
        if(superHero!=null){
            superHero.setIdNumber(idNumber);
        }
        return superHero;
    }
    
    @Query
    public String currentLocation(@Name("superHero")@Source SuperHero hero) throws GraphQLException {
        LOG.log(Level.INFO, "currentLocation invoked [{0}]", hero);
        final String heroName = hero.getName();
        return heroLocator.getHeroLocation(heroName)
                .orElseThrow(() -> {
                    return new GraphQLException("Cannot find location for " + heroName,
                            GraphQLException.ExceptionType.DataFetchingException);
                });
    }

    @Name("secretToken")
    public String generateSecretToken(@Source SuperHero hero,
                                      @DefaultValue("true") 
                                      @Name("maskFirstPart") boolean maskFirstPart) throws GraphQLException {
        LOG.log(Level.INFO, "generateSecretToken invoked [{0}],[{1}]", new Object[]{hero,maskFirstPart});
        
        String uuid = UUID.randomUUID().toString();
        if(maskFirstPart){
            return uuid.substring(0,uuid.length()-4).replaceAll("[A-Za-z0-9]", "*") + uuid.substring(uuid.length()-4,uuid.length());
        }else{
            return uuid;
        }
    }
    
    @Mutation("setRivalTeam")
    public Team setRivalTeam(@Name("teamName") String teamName, @Name("rivalTeam") Team rivalTeam)
            throws UnknownTeamException {

        LOG.log(Level.INFO, "setRivalTeam invoked [{0}],[{1}]", new Object[]{teamName, rivalTeam});
        Team team = heroDB.getTeam(teamName);
        team.setRivalTeam(rivalTeam);
        return team;
    }

    @Query
    public Collection<SuperHero> allHeroesWithError() throws GraphQLException {
        LOG.info("allHeroesWithError invoked");
        List<SuperHero> partialHeroes = new ArrayList<>();
        for (SuperHero hero : heroDB.getAllHeroes()) {
            if ("Starlord".equals(hero.getName())) {
                partialHeroes.add(null);
            } else {
                partialHeroes.add(hero);
            }
        }
        throw new GraphQLException("Failed to find one or more heroes", partialHeroes);
    }

    @Query
    public Collection<SuperHero> allHeroesWithSpecificError() throws SuperHeroLookupException {
        LOG.info("allHeroesWithSpecificError invoked");
        List<SuperHero> partialHeroes = new ArrayList<>();
        for (SuperHero hero : heroDB.getAllHeroes()) {
            if (!"Spider Man".equals(hero.getName())) {
                partialHeroes.add(hero);
            }
        }
        throw new SuperHeroLookupException("Failed to find one or more heroes", partialHeroes);
    }

    @Query
    public Item getItemById(@Name("id") long id) {
        LOG.log(Level.INFO, "getItemById invoked [{0}]", id);
        for (SuperHero hero : allHeroes()) {
            for (Item item : hero.getEquipment()) {
                if (id == item.getId()) {
                    return item;
                }
            }
        }
        return null;
    }

    @Mutation
    public Team createNewTeam(@Name("newTeam") Team newTeam) {
        LOG.log(Level.INFO, "createNewTeam invoked [{0}]", newTeam);
        List<SuperHero> members = newTeam.getMembers();
        Team team = heroDB.createNewTeam(newTeam.getName());
        if (members != null && members.size() > 0) {
            team.setMembers(members);
        }
        team.setRivalTeam(newTeam.getRivalTeam());
        return team;
    }

    @Mutation
    public Team removeTeam(@Name("teamName") String teamName) throws UnknownTeamException {
        LOG.log(Level.INFO, "removeTeam invoked [{0}]", teamName);
        return heroDB.removeTeam(teamName);
    }
    
    private Collection<SuperHero> allHeroesByFilter(Predicate<SuperHero> predicate) {
        return heroDB.getAllHeroes()
                .stream()
                .filter(predicate)
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
