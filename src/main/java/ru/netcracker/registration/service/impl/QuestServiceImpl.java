package ru.netcracker.registration.service.impl;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.netcracker.registration.mail.mailer.GmailSender;
import ru.netcracker.registration.mail.mailer.NetcrackerMailCredentials;
import ru.netcracker.registration.model.*;
import ru.netcracker.registration.model.DTO.QuestDTO;
import ru.netcracker.registration.model.DTO.SpotConfirmationDTO;
import ru.netcracker.registration.model.DTO.SpotDTO;
import ru.netcracker.registration.model.DTO.UserProgressDTO;
import ru.netcracker.registration.model.converter.QuestConverter;
import ru.netcracker.registration.model.converter.UserProgressConverter;
import ru.netcracker.registration.other.exception.ReportException;
import ru.netcracker.registration.repository.*;
import ru.netcracker.registration.service.QuestService;
import ru.netcracker.registration.service.SpotInQuestService;
import ru.netcracker.registration.service.SpotService;

import javax.jws.soap.SOAPBinding;
import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.stream.Collectors;

@Service("QuestService")
@Transactional
public class QuestServiceImpl implements QuestService {


    @Autowired
    QuestRepository questRepository;
    @Autowired
    UserProgressRepository userProgressRepository;
    @Autowired
    UserSpotProgressRepository userSpotProgressRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PhotoRepository photoRepository;
    @Autowired
    PhotoTypeRepository photoTypeRepository;

    @Autowired
    SpotInQuestService spotInQuestService;
    @Autowired
    SpotService spotService;

    @Autowired
    SimpMessagingTemplate messagingTemplate;

    @Autowired
    ReportRepository reportRepository;

    @Override
    public QuestDTO getById(Long id) {
        return QuestConverter.convertToDTO(questRepository.findOne(id));
    }

    @Override
    public Quest getEntityById(Long id) {
        return questRepository.findOne(id);
    }

    @Override
    public void reportQuest(Long questId, String userMail) throws ReportException {

        Quest quest = getEntityById(questId);
        User user = userRepository.findByEmail(userMail);
        User owner = quest.getOwnerId();
        if (reportRepository.findByQuestIdAndUserId(quest, user) == null) {
            quest.setReports(quest.getReports() + 1);
            owner.setReports(owner.getReports() + 1);
            questRepository.save(quest);
            userRepository.save(owner);
            Report report = new Report();
            report.setQuestId(quest);
            report.setUserId(user);
            reportRepository.save(report);
        } else {
            throw new ReportException("Report has been already send");
        }
    }

    @Override
    public void approve(Long id) {
        Quest quest = getEntityById(id);
        reportRepository.deleteAllByQuestId(quest);
        quest.setReports(0);
        questRepository.save(quest);
    }

    @Override
    public void ban(Long id) {
        Quest quest = getEntityById(id);
        quest.setStatus(3);
        questRepository.save(quest);
    }

    @Override
    public List<QuestDTO> getReported(Integer reportCount) {
        return questRepository.findAllByReportsGreaterThanEqual(reportCount)
                .stream()
                .map(QuestConverter::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<Quest> getByName(String name) {
        return questRepository.findAllByName(name);
    }

    @Override
    public QuestDTO getOneByName(String name) {
        Quest quest = getOneByNameNotDTO(name);
        if (quest != null)
            return QuestConverter.convertToDTO(quest);
        return null;
    }


    private Quest getOneByNameNotDTO(String name) {
        List<Quest> quests = questRepository.findAllByName(name);
        if (quests != null && quests.size() > 0) {
            return quests.get(0);
        }
        return null;
    }

    @Override
    public List<Quest> getAll() {
        return (List<Quest>) questRepository.findAll();
    }

    @Override
    public boolean delete(Long id) {
        questRepository.delete(id);
        return true;
    }

    @Override
    public boolean delete(Quest quest) {
        questRepository.delete(quest);
        return true;
    }

    @Override
    public Quest save(Quest quest) {
        return questRepository.save(quest);
    }

    @Override
    public void save(QuestDTO questDTO, User user) {
        Quest quest = new Quest();
        quest.setName(questDTO.getName());
        quest.setDescription(questDTO.getDescription());
        quest.setReward(questDTO.getReward());
        quest.setUploadDate(questDTO.getUploadDate());
        quest.setNumberOfParticipants(questDTO.getNumberOfParticipants());
        quest.setNumberOfJoiners(questDTO.getNumberOfJoiners());
        quest.setStatus(0);
        quest.setOwnerId(user);
        quest.setReports(0);
        for (SpotDTO spotDTO : questDTO.getSpots()) {
            Spot spot = new Spot();
            SpotInQuest spotInQuest = new SpotInQuest();
            spot.setUploadDate(questDTO.getUploadDate());
            spot.setName(spotDTO.getName());
            spot.setLat(spotDTO.getLat());
            spot.setLng(spotDTO.getLng());
            Photo photo = new Photo();
            photo.setUrl(spotDTO.getPhotos().stream().findFirst().get().getUrl());
            photo.setUploadDate(questDTO.getUploadDate());
            photo.setUser(user);
            photo.setSpotBySpotId(spot);
            photo.setPhotoTypeByTypeId(photoTypeRepository.findByName("spot"));
            spot.getPhotoBySpotId().add(photo);
            spotInQuest.setQuest(quest);
            spotInQuest.setSpotBySpotId(spot);
            spotInQuest.setPhotoByPhotoId(spot.getPhotoBySpotId().stream().findFirst().get());
            quest.getSpotInQuests().add(spotInQuest);
        }

        int cost = questCostCalculation(quest);
        if (user.getBalance() - cost >= 0 && cost > 0) {
            user.setBalance(user.getBalance() - cost);
            user.setBusinessBalance(user.getBusinessBalance() - cost);
            questRepository.save(quest);
            userRepository.save(user);
        }
    }

    @Override
    public List<QuestDTO> getAllToDTO() {
        List<QuestDTO> res = new ArrayList<>();
        for (Quest q : questRepository.findAll()) {
            res.add(QuestConverter.convertToDTO(q));
        }
        return res;
    }

    @Override
    public List<QuestDTO> getAllByOwner(String email) {
        List<QuestDTO> res = new ArrayList<>();
        User owner = userRepository.findByEmail(email);
        for (Quest q : questRepository.findAllByOwnerId(owner)) {
            res.add(QuestConverter.convertToDTO(q));
        }
        return res;
    }

    @Override
    public List<QuestDTO> getAllInRange(double lat, double lng, double range) {
        List<Quest> quests = getAll();
        List<QuestDTO> res = new ArrayList<>();
        for (Quest quest : quests) {
            if (isQuestActive(quest)) {
                Spot spot = quest.getSpotInQuests().stream().findFirst().get().getSpotBySpotId();
                if (spotService.distFrom(lat, lng, spot) <= range)
                    res.add(QuestConverter.convertToDTO(quest));
            }
        }
        return res;
    }

    @Override
    public List<UserProgressDTO> getUserProgressByUser(String email) {
        User user = userRepository.findByEmail(email);
        List<UserProgress> userProgressList = userProgressRepository.findAllByUserByUserId(user);
        List<UserProgressDTO> userProgressDTOList = new ArrayList<>();
        for (UserProgress userProgress : userProgressList) {
            userProgressDTOList.add(UserProgressConverter.convertToDTO(userProgress));
        }
        return userProgressDTOList;
    }

    @Override
    public UserProgressDTO getUserProgressByUserAndQuest(String email, Long questId) {
        User user = userRepository.findByEmail(email);
        Quest quest = questRepository.findOne(questId);
        UserProgress userProgress = userProgressRepository.findByUserByUserIdAndAndQuestByQuestId(user, quest);
        if (userProgress != null) {
            return UserProgressConverter.convertToDTO(userProgress);
        }
        return null;
    }

    @Override
    public void userJoinQuest(String email, Long questId) {
        UserProgress progress = new UserProgress();
        Quest quest = questRepository.findOne(questId);
        if (isQuestActive(quest)) {
            quest.setNumberOfJoiners(quest.getNumberOfJoiners() + 1);
            if (isQuestFull(quest)) {
                quest.setStatus(1);
            }
            questRepository.save(quest);
            progress.setUserByUserId(userRepository.findByEmail(email));
            progress.setQuestByQuestId(questRepository.findOne(questId));
            progress.setTakingDate(new java.sql.Date(Calendar.getInstance().getTime().getTime()));

            userProgressRepository.save(progress);
        }
    }

    @Override
    public void userCloseQuest(Long questId) {
        Quest quest = questRepository.findOne(questId);
        String ownerEmail = quest.getOwnerId().getEmail();
        List<SpotConfirmationDTO> confirmationDTOS = getSpotConfirmationsForOwner(ownerEmail);
        if (confirmationDTOS.isEmpty() && (isQuestActive(quest) || isQuestInvisible(quest))) {
            quest.setStatus(2);
        }
        questRepository.save(quest);
    }

    @Override
    public List<Quest> findInactive() {

        List<Quest> res = new ArrayList<>();
        List<Quest> quests = (List<Quest>) questRepository.findAllByStatusEquals(0);
        for(Quest q: quests){
            List<SpotInQuest> spotInQuests = (List<SpotInQuest>) q.getSpotInQuests();
            for(SpotInQuest spot: spotInQuests){
                if(ChronoUnit.DAYS.between(LocalDate.now(), (Temporal) spot.getSpotBySpotId().getUploadDate())>20){
                    List<UserSpotProgress> usp = (List<UserSpotProgress>) spot.getUserSpotProgressesBySpotsInQuestsId();
                    for(UserSpotProgress progress: usp){
                        if(progress.getSpotStatus().equals("Unconfirmed")){
                            res.add(q);
                            break;
                        }
                    }
                }
            }
        }
        return res;
    }



    @Override
    public List<Quest> findSuspicious() {
        List<Quest> res = new ArrayList<>();
        List<Quest> quests = (List<Quest>) questRepository.findAllByStatusEquals(0);
        for (Quest q: quests){
            if((q.getSpotInQuests().size()<3||q.getNumberOfParticipants()>40) && q.getReward() > 100){
                res.add(q);
            }
        }
        return res;
    }

    @Override
    public void deleteOdd() {
        questRepository.deleteAllByStatusGreaterThanEqual(3);
    }

    @Override
    public void deleteIterable(Iterable<Quest> quests) {
        questRepository.delete(quests);
    }

    @Override
    public void userCompleteSpot(String email, Long questId, Long spotId) {
        SpotInQuest spotInQuest = spotInQuestService.getBySpotAndQuest(spotId, questId);
        User user = userRepository.findByEmail(email);
        Quest quest = questRepository.findOne(questId);
        UserProgress userProgress = userProgressRepository.findByUserByUserIdAndAndQuestByQuestId(user, quest);

        if (quest.getStatus() < 2) {
            Date currentDate = new java.sql.Date(Calendar.getInstance().getTime().getTime());
            UserSpotProgress userSpotProgress = new UserSpotProgress();
            userSpotProgress.setDateComplete(currentDate);
            userSpotProgress.setSpotsInQuestsBySpotInQuestId(spotInQuest);
            userSpotProgress.setSpotStatus("Unconfirmed");
            userSpotProgress.setUserProgressByUserProgressId(userProgress);

            userSpotProgressRepository.save(userSpotProgress);

            messagingTemplate.convertAndSendToUser(
                    quest.getOwnerId().getEmail(),
                    "/confirmation",
                    "Somebody has complete spot in your quest. Check confirmations page"
            );
        }
    }

    public List<SpotConfirmationDTO> getSpotConfirmationsForOwner(String email) {
        User owner = userRepository.findByEmail(email);
        List<UserSpotProgress> progresses = userSpotProgressRepository.getAllUnconfirmedByQuestOwner(owner);
        List<SpotConfirmationDTO> confirmationDTOS = new ArrayList<>();
        for (UserSpotProgress progress : progresses) {
            if (!progress.getUserProgressByUserProgressId().getUserByUserId().equals(owner)) {
                SpotConfirmationDTO confirmationDTO = new SpotConfirmationDTO();
                confirmationDTO.setUserSpotProgressId(progress.getId());
                confirmationDTO.setUploadDate(progress.getDateComplete());
                confirmationDTO.setMainPhotoURL(progress.getSpotsInQuestsBySpotInQuestId().getPhotoByPhotoId().getUrl());
                confirmationDTO.setQuestName(progress.getSpotsInQuestsBySpotInQuestId().getQuest().getName());
                confirmationDTO.setSpotName(progress.getSpotsInQuestsBySpotInQuestId().getSpotBySpotId().getName());

                Photo photo = photoRepository.findByUserAndAndSpotBySpotId(progress.getUserProgressByUserProgressId().getUserByUserId(), progress.getSpotsInQuestsBySpotInQuestId().getSpotBySpotId());
                confirmationDTO.setPhotoURL(photo.getUrl());

                confirmationDTOS.add(confirmationDTO);
            }
        }

        return confirmationDTOS;
    }

    public void setConfirmation(String email, Long userSpotProgressId, Boolean confirm) throws Exception {
        UserSpotProgress userSpotProgress = userSpotProgressRepository.findOne(userSpotProgressId);
        User owner = userSpotProgress.getSpotsInQuestsBySpotInQuestId().getQuest().getOwnerId();
        User user = userSpotProgress.getUserProgressByUserProgressId().getUserByUserId();
        Quest quest = userSpotProgress.getUserProgressByUserProgressId().getQuestByQuestId();
        if (!owner.getEmail().equals(email)) {
            throw new Exception("User is not owner of the quest");
        }

        GmailSender sender = new GmailSender(NetcrackerMailCredentials.email, NetcrackerMailCredentials.password);

        if (confirm) {
            userSpotProgress.setSpotStatus("Confirmed");
            userSpotProgressRepository.save(userSpotProgress);

            //if quest is totally completed and confirmed
            if (isQuestConfirmedAndCompleted(user, quest)) {
                user.setBalance(user.getBalance() + quest.getReward());
                userRepository.save(user);
                Date currentDate = new java.sql.Date(Calendar.getInstance().getTime().getTime());
                UserProgress userProgress = userSpotProgress.getUserProgressByUserProgressId();
                userProgress.setDateComplete(currentDate);
                userProgressRepository.save(userProgress);
            }

            //notify by email
            String body = String.format(
                    "Dear %s! We are happy to tell that Your photo in quest \"%s\": spot \"%s\" was confirmed.",
                    user.getFirstName(),
                    quest.getName(),
                    userSpotProgress.getSpotsInQuestsBySpotInQuestId().getSpotBySpotId().getName()
            );
            sender.sendMail("Photo confirmed", body, "netcracker", user.getEmail());
        } else {
            Photo photo = photoRepository.findByUserAndAndSpotBySpotId(user, userSpotProgress.getSpotsInQuestsBySpotInQuestId().getSpotBySpotId());
            userSpotProgressRepository.delete(userSpotProgress);
            photoRepository.delete(photo);

            //notify by email
            String body = String.format(
                    "Dear %s! Unfortunately, Your photo in quest \"%s\": spot \"%s\" was rejected.",
                    user.getFirstName(),
                    quest.getName(),
                    userSpotProgress.getSpotsInQuestsBySpotInQuestId().getSpotBySpotId().getName()
            );
            sender.sendMail("Photo rejected", body, "netcracker", user.getEmail());
        }
    }

    @Override
    public QuestDTO getTopQuest() {

        Map<Quest, Integer> takenQuests = new HashMap<>();
        List<UserProgress> progress = (List<UserProgress>) userProgressRepository.findAll();
        if (progress.isEmpty()){
            QuestDTO questDTO = QuestConverter.convertToDTO(questRepository.findOne(37L));
            return questDTO;
        }
            for (UserProgress up : progress) {
                if (up.getDateComplete() != null) {
//                QuestDTO quest = QuestConverter.convertToDTO(up.getQuestByQuestId());
                    if (takenQuests.containsKey(up.getQuestByQuestId())) {
                        takenQuests.put(up.getQuestByQuestId(), takenQuests.get(up.getQuestByQuestId()) + 1);
                    } else {
                        takenQuests.put(up.getQuestByQuestId(), 1);
                    }
                }
            }

//        for (User u : users) {
//            List<UserProgressDTO> userQuests = getUserProgressByUser(u.getEmail());
//            userQuests = userQuests.stream().filter(quest -> quest.getDateComplete() != null).collect(Collectors.toList());
//            for (UserProgressDTO quest : userQuests) {
//                if (takenQuests.containsKey(quest.getQuest())) {
//                    takenQuests.replace(quest.getQuest(), takenQuests.get(quest.getQuest()) + 1);
//                } else {
//                    takenQuests.put(quest.getQuest(), 1);
//                }
//            }
//        }
        return QuestConverter.convertToDTO(Collections.max(takenQuests.entrySet(), Comparator.comparingInt(Map.Entry::getValue)).getKey());
    }

    public int questCostCalculation(Quest quest) {
        int k = quest.getSpotInQuests().size();
        int n = 1;
        switch (k) {
            case 2:
                n = 2;
                break;
            case 3:
                n = 4;
                break;
            case 4:
                n = 7;
                break;
            case 5:
                n = 11;
                break;
        }
        return quest.getNumberOfParticipants() * quest.getReward() * n;
    }

    private boolean isQuestActive(Quest quest) {
        if (quest.getStatus() == 0) {
            return true;
        } else {
            return false;
        }

    }

    private boolean isQuestFull(Quest quest) {
        if (quest.getNumberOfParticipants().equals(quest.getNumberOfJoiners())) {
            return true;
        } else {
            return false;
        }
    }

    private boolean isQuestInvisible(Quest quest) {
        if (quest.getStatus() == 1) {
            return true;
        } else {
            return false;
        }
    }

    private boolean isQuestClosed(Quest quest) {
        if (quest.getStatus() == 2) {
            return true;
        } else {
            return false;
        }
    }

    private boolean isQuestBanned(Quest quest) {
        if (quest.getStatus() == 3) {
            return true;
        } else {
            return false;
        }
    }

    private boolean isQuestConfirmedAndCompleted(User user, Quest quest) {
        UserProgress userProgress = userProgressRepository.findByUserByUserIdAndAndQuestByQuestId(user, quest);
        int count = 0;
        if (userProgress.getQuestByQuestId().getSpotInQuests().size() == userProgress.getUserSpotProgressesByUserProgressId().size()) {
            for (UserSpotProgress usp : userProgress.getUserSpotProgressesByUserProgressId()) {
                if (usp.getSpotStatus().equals("Confirmed"))
                    count++;
            }
            if (count == userProgress.getUserSpotProgressesByUserProgressId().size())
                return true;
        }
        return false;
    }

}