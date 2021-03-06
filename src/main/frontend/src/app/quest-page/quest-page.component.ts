import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {QuestDTO} from '../quest/quest.model';
import {UserProgressDTO} from '../quest/user-progress.model';
import {QuestService} from '../quest/quest.service';
import {Observable} from 'rxjs/Observable';
import {Status} from 'tslint/lib/runner';
import {marker} from '../map/map.component';
import {UserService} from "../user/user.service";
import {User} from "../user/user.model";

@Component({
  selector: 'app-quest-page',
  templateUrl: './quest-page.component.html',
  styleUrls: ['./quest-page.component.css']
})
export class QuestPageComponent implements OnInit {
  private sub: any;
  markers: marker[] = [];
  mapLat: number = 51.0;
  mapLng: number = 7.0;
  quest: QuestDTO = new QuestDTO('', '', null, 0, 10, 0, 0);
  userProgress: UserProgressDTO = new UserProgressDTO(null, null);
  photosToUpload: string[] = [];
  photoTitles:string[]=[];
  errorMsg: string;
  placesLeft: number;
  loading: boolean = false;
  user: User;
  reported: boolean = false;


  constructor(private route: ActivatedRoute,
              public questService: QuestService,
              private router: Router,
              private userService: UserService) {
  }

  ngOnInit() {
    this.loading=true;
    this.userService.getCurrentUser()
      .subscribe(
        (user: User) => {
          this.user = user;
          this.loading=false;
        },
        (error) => {
          console.log(error);
          this.loading=false;
        });

    this.sub = this.route.params.subscribe(params => {
      this.loadUserProgress(+params['quest-id']);

      this.questService.getQuestById(params['quest-id'])
        .subscribe(
          (quest: any) => {
            this.quest = quest;
            this.quest.uploadDate = new Date(this.quest.uploadDate);
            this.updateMarkers();
            this.calculatePlaces();
          },
          (error) => {
            console.log(error);
            this.router.navigate(['/map']);
          });
    });
  }

  loadUserProgress(questId: number) {
    this.questService.getUserProgressByQuest(questId)
      .subscribe(
        (progress: any) => {
          this.userProgress = progress;
          this.userProgress.takingDate = new Date(this.userProgress.takingDate);
          if (this.userProgress.dateComplete != null)
            this.userProgress.dateComplete = new Date(this.userProgress.dateComplete);
        },
        (error) => {
          console.log(error);
        });
  }

  updateMarkers() {
    for (let spot of this.quest.spots) {
      this.markers.push({
        lat: parseFloat(spot.lat),
        lng: parseFloat(spot.lng),
        label: spot.name,
        name: null,
        iconUrl: null,
        description: null,
        draggable: false,
        quests: null,
        photos: null
      });
    }
    this.mapLat = this.markers[0].lat;
    this.mapLng = this.markers[0].lng;
  }

  banQuest(quest) {
    this.questService.banQuest(quest)
      .subscribe(
        (obj: string) => {
          window.alert(obj);
        }
      )
  }

  joinQuest() {
    this.loading = true;
    this.questService.joinQuest(this.quest.questId).catch((response: Response) => {
      if (response.status == 401)
        this.router.navigate(['/login']);
      else
        this.writeError(response.text());
      this.loading = false;
      return Observable.throw(response);
    })
      .subscribe(
        (response: any) => {
          console.log(response);
          this.loading = false;
          this.loadUserProgress(this.quest.questId);
        });
  }

  closeQuest() {
    this.questService.closeQuest(this.quest.questId).catch((response: Response) => {
      if (response.status == 401)
        this.router.navigate(['/login']);
      else
        this.writeError(response.text());
      return Observable.throw(response);
    })
      .subscribe(
        (response: any) => {
          console.log(response);
          this.loading = false;
          this.quest.status = 2;
        });
  }

  selectFile(event, spotId: number) {
    console.log('selected ' + spotId);
    this.loading = true;
    this.questService.uploadSpotPhoto(event.target.files[0]).catch((response) => {
      this.loading = false;
      return Observable.throw(response);
    }).subscribe((data) => {
        console.log(data);
        this.loading = false;
        this.photosToUpload[spotId] = data;
        this.photoTitles[spotId]=event.target.files[0].name;
      },
      (error) => {
        this.loading = false;
        console.log(error);
      });
  }

  postPhoto(spotId: number) {
    if (this.photosToUpload[spotId] != null) {
      this.loading = true;
      this.questService.postSpotPhoto(this.photosToUpload[spotId], this.quest.questId, spotId)
        .subscribe(
          (response: any) => {
            console.log(response);
            this.loading = false;
            this.loadUserProgress(this.quest.questId);
          },
          (error) => {
            this.loading = false;
            console.log(error);
          });
    }
    else {
      console.log('no file loaded');
    }
  }

  reportQuest(questId) {
    this.questService.reportQuest(questId)
      .subscribe((obj: string) => {
          window.alert(obj);
        },
        (error) => console.log(error),
        () => this.reported = true);
  }


  writeError(error) {
    document.getElementById('collapseMessage').classList.add('show');
    this.errorMsg = error;
  }

  calculatePlaces() {
    this.placesLeft = this.quest.numberOfParticipants - this.quest.numberOfJoiners;
  }

}
