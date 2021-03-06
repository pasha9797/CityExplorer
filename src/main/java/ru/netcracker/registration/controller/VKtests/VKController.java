package ru.netcracker.registration.controller.VKtests;

import com.vk.api.sdk.callback.CallbackApi;
import com.vk.api.sdk.client.ClientResponse;
import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.UserAuthResponse;
import com.vk.api.sdk.objects.account.Info;
import com.vk.api.sdk.objects.account.UserSettings;
import com.vk.api.sdk.objects.apps.responses.GetResponse;
import com.vk.api.sdk.objects.photos.Photo;
import com.vk.api.sdk.objects.photos.PhotoUpload;
import com.vk.api.sdk.objects.photos.responses.WallUploadResponse;
import com.vk.api.sdk.objects.users.UserXtrCounters;
import com.vk.api.sdk.objects.wall.responses.PostResponse;
import com.vk.api.sdk.objects.wall.responses.SearchResponse;
import com.vk.api.sdk.queries.users.UserField;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import ru.netcracker.registration.model.DTO.VkUserDTO;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/userapi")
public class VKController {

    private static final int APP_ID = 6346913;
    private static final String SECURE_KEY = "KSyvMNJDmCkP7bg6K1ms";
    private static final String SERVICE_TOKEN = "11195acd11195acd11195acd671179826c1111911195acd4b7318340fa7ea0bd93d7565";
    private String code = "";
    private static final int MY_ID = 30327533;
    private static String accessToken = "";
    private static int id;
    private VkApiClient vk;
    private UserActor actor;
    private static String TOKEN = "access_token=d6b2671d883049f82d7d6f5d3c96193cee0f02f797684d1871178d0314069317a9cca8a2a40bd4e61da58";


    public VKController() {
        TransportClient transportClient = HttpTransportClient.getInstance();
        vk = new VkApiClient(transportClient);
    }


//    @GetMapping("/photo")
//    public void getPhoto(HttpServletResponse response, HttpServletRequest request) {
//        SearchResponse searchResponse = null;
//        try {
//            System.out.println("photo");
//            searchResponse = vk.wall().search(actor)
//                    .ownerId(actor.getId())
//                    .query("солдаты").execute();
//            //searchResponse.getItems().get(0).getAttachments().get(1).getPhoto().getPhoto1280();
//        } catch (ApiException | ClientException e) {
//            e.printStackTrace();
//        }
//        try {
//            response.sendRedirect(searchResponse.getItems().get(0).getAttachments().get(0).getPhoto().getPhoto1280());
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
////            ModelAndView mav = new ModelAndView("redirect:https://oauth.vk.com/authorize?client_id=6346913&display=page&redirect_uri=https://oauth.vk.com/blank.html&scope=friends,wall,photos&response_type=code&v=5.71");
////            return mav;
//        //test.forEach((key, value) -> System.out.println(key + "=-=-=" + value));
//    }

    @GetMapping("/signin/{code}")
    public @ResponseBody ResponseEntity<?> signIn(@PathVariable String code) {
        UserAuthResponse authResponse;
        try {
            actor = new UserActor(Integer.valueOf(code.split("~")[1]), code.split("~")[0]);

            UserXtrCounters userInfo = vk.users().get(actor).userIds(code.split("~")[1]).fields(UserField.CITY).execute().get(0);
            VkUserDTO vkUser = new VkUserDTO();

            File picture = new File("/home/typhoon/labs/JavaLabs/netcracker/CityExplorer/src/main/java/ru/netcracker/registration/controller/VKtests/Logo.png");
            PhotoUpload serverResponse = vk.photos().getWallUploadServer(actor).execute();
            WallUploadResponse uploadResponse = vk.upload().photoWall(serverResponse.getUploadUrl(), picture).execute();
            // vk.wall().post(actor).message("#cityexplorer").

            List<Photo> photoList = vk.photos()
                    .saveWallPhoto(actor, uploadResponse.getPhoto())
                    .server(uploadResponse.getServer())
                    .hash(uploadResponse.getHash())
                    .execute();
            Photo photo = photoList.get(0);


            vkUser.setPhotoUrl(photo.getPhoto604());
            //vkUser.setPhotoUrl(vk.photos().get(actor).albumId("profile").rev(true).execute().getItems().get(0).getPhoto604());
            vkUser.setFirstName(userInfo.getFirstName());
            vkUser.setLastName(userInfo.getLastName());
            vkUser.setCity(userInfo.getCity().getTitle());
            return new ResponseEntity<>(vkUser, HttpStatus.OK);
        } catch (ApiException | ClientException e) {
            e.printStackTrace();
            return new ResponseEntity<Object>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }



//    @GetMapping("/upload")
//    public void uploadPhoto(HttpServletResponse response) {
//        try {
//            File picture = new File("/home/typhoon/labs/JavaLabs/netcracker/CityExplorer/src/main/java/ru/netcracker/registration/controller/VKtests/Logo.png");
//            PhotoUpload serverResponse = vk.photos().getWallUploadServer(actor).execute();
//            WallUploadResponse uploadResponse = vk.upload().photoWall(serverResponse.getUploadUrl(), picture).execute();
//            // vk.wall().post(actor).message("#cityexplorer").
//
//            List<Photo> photoList = vk.photos()
//                    .saveWallPhoto(actor, uploadResponse.getPhoto())
//                    .server(uploadResponse.getServer())
//                    .hash(uploadResponse.getHash())
//                    .execute();
//            Photo photo = photoList.get(0);
//            String attachId = "photo" + photo.getOwnerId() + "_" + photo.getId();
//            //нет доступа
//            vk.wall().post(actor).attachments(attachId).message("#cityexplorer").confirm(true).execute();
//            //sdd
//
//            response.getHeaderNames();
//
//        } catch (ApiException | ClientException e) {
//            e.getLocalizedMessage();
//        }
//    }
}
