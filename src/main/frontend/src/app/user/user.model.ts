/**
 * Класс пользователя на frontend
 */
import {GroupId} from './group-id.model';

export class User {
  public id: number;
  public firstName: string;
  public lastName: string;
  public birthday: string;
  public email: string;
  public password: string;
  public registrationDate: string;
  public balance: number;
  public businessBalance: number;
  public groupID: GroupId;


  constructor(firstName: string, lastName: string, birthday: string, email: string, password: string, registrationDate: string, balance: number, groupID: GroupId, businessBalance: number) {
    this.firstName = firstName;
    this.lastName = lastName;
    this.birthday = birthday;
    this.email = email;
    this.password = password;
    this.registrationDate = registrationDate;
    this.balance = balance;
    this.businessBalance = this.businessBalance;
    this.groupID = groupID;
  }
}
