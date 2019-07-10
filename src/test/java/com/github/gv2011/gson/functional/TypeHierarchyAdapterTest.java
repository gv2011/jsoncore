/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.gv2011.gson.functional;

import java.lang.reflect.Type;

import com.github.gv2011.gson.Gson;
import com.github.gv2011.gson.GsonBuilder;
import com.github.gv2011.gson.JsonDeserializationContext;
import com.github.gv2011.gson.JsonDeserializer;
import com.github.gv2011.gson.JsonElement;
import com.github.gv2011.gson.JsonObject;
import com.github.gv2011.gson.JsonParseException;
import com.github.gv2011.gson.JsonPrimitive;
import com.github.gv2011.gson.JsonSerializationContext;
import com.github.gv2011.gson.JsonSerializer;

import junit.framework.TestCase;

/**
 * Test that the hierarchy adapter works when subtypes are used.
 */
public final class TypeHierarchyAdapterTest extends TestCase {

  public void testTypeHierarchy() {
    final Manager andy = new Manager();
    andy.userid = "andy";
    andy.startDate = 2005;
    andy.minions = new Employee[] {
        new Employee("inder", 2007),
        new Employee("joel", 2006),
        new Employee("jesse", 2006),
    };

    final CEO eric = new CEO();
    eric.userid = "eric";
    eric.startDate = 2001;
    eric.assistant = new Employee("jerome", 2006);

    eric.minions = new Employee[] {
        new Employee("larry", 1998),
        new Employee("sergey", 1998),
        andy,
    };

    final Gson gson = new GsonBuilder()
        .registerTypeHierarchyAdapter(Employee.class, new EmployeeAdapter())
        .setPrettyPrinting()
        .create();

    final Company company = new Company();
    company.ceo = eric;

    final String json = gson.toJson(company, Company.class);
    assertEquals("{\n" +
        "  \"ceo\": {\n" +
        "    \"userid\": \"eric\",\n" +
        "    \"startDate\": 2001,\n" +
        "    \"minions\": [\n" +
        "      {\n" +
        "        \"userid\": \"larry\",\n" +
        "        \"startDate\": 1998\n" +
        "      },\n" +
        "      {\n" +
        "        \"userid\": \"sergey\",\n" +
        "        \"startDate\": 1998\n" +
        "      },\n" +
        "      {\n" +
        "        \"userid\": \"andy\",\n" +
        "        \"startDate\": 2005,\n" +
        "        \"minions\": [\n" +
        "          {\n" +
        "            \"userid\": \"inder\",\n" +
        "            \"startDate\": 2007\n" +
        "          },\n" +
        "          {\n" +
        "            \"userid\": \"joel\",\n" +
        "            \"startDate\": 2006\n" +
        "          },\n" +
        "          {\n" +
        "            \"userid\": \"jesse\",\n" +
        "            \"startDate\": 2006\n" +
        "          }\n" +
        "        ]\n" +
        "      }\n" +
        "    ],\n" +
        "    \"assistant\": {\n" +
        "      \"userid\": \"jerome\",\n" +
        "      \"startDate\": 2006\n" +
        "    }\n" +
        "  }\n" +
        "}", json);

    final Company copied = gson.fromJson(json, Company.class);
    assertEquals(json, gson.toJson(copied, Company.class));
    assertEquals(copied.ceo.userid, company.ceo.userid);
    assertEquals(copied.ceo.assistant.userid, company.ceo.assistant.userid);
    assertEquals(copied.ceo.minions[0].userid, company.ceo.minions[0].userid);
    assertEquals(copied.ceo.minions[1].userid, company.ceo.minions[1].userid);
    assertEquals(copied.ceo.minions[2].userid, company.ceo.minions[2].userid);
    assertEquals(((Manager) copied.ceo.minions[2]).minions[0].userid,
        ((Manager) company.ceo.minions[2]).minions[0].userid);
    assertEquals(((Manager) copied.ceo.minions[2]).minions[1].userid,
        ((Manager) company.ceo.minions[2]).minions[1].userid);
  }

  public void testRegisterSuperTypeFirst() {
    final Gson gson = new GsonBuilder()
        .registerTypeHierarchyAdapter(Employee.class, new EmployeeAdapter())
        .registerTypeHierarchyAdapter(Manager.class, new ManagerAdapter())
        .create();

    final Manager manager = new Manager();
    manager.userid = "inder";

    final String json = gson.toJson(manager, Manager.class);
    assertEquals("\"inder\"", json);
    final Manager copied = gson.fromJson(json, Manager.class);
    assertEquals(manager.userid, copied.userid);
  }

  /** This behaviour changed in Gson 2.1; it used to throw. */
  public void testRegisterSubTypeFirstAllowed() {
    new GsonBuilder()
        .registerTypeHierarchyAdapter(Manager.class, new ManagerAdapter())
        .registerTypeHierarchyAdapter(Employee.class, new EmployeeAdapter())
        .create();
  }

  static class ManagerAdapter implements JsonSerializer<Manager>, JsonDeserializer<Manager> {
    @Override public Manager deserialize(final JsonElement json, final Type typeOfT, final JsonDeserializationContext context) {
      final Manager result = new Manager();
      result.userid = json.getAsString();
      return result;
    }
    @Override public JsonElement serialize(final Manager src, final Type typeOfSrc, final JsonSerializationContext context) {
      return new JsonPrimitive(src.userid);
    }
  }

  static class EmployeeAdapter implements JsonSerializer<Employee>, JsonDeserializer<Employee> {
    @Override public JsonElement serialize(final Employee employee, final Type typeOfSrc,
        final JsonSerializationContext context) {
      final JsonObject result = new JsonObject();
      result.add("userid", context.serialize(employee.userid, String.class));
      result.add("startDate", context.serialize(employee.startDate, long.class));
      if (employee instanceof Manager) {
        result.add("minions", context.serialize(((Manager) employee).minions, Employee[].class));
        if (employee instanceof CEO) {
          result.add("assistant", context.serialize(((CEO) employee).assistant, Employee.class));
        }
      }
      return result;
    }

    @Override public Employee deserialize(final JsonElement json, final Type typeOfT,
        final JsonDeserializationContext context) throws JsonParseException {
      final JsonObject object = json.getAsJsonObject();
      Employee result = null;

      // if the employee has an assistant, she must be the CEO
      final JsonElement assistant = object.get("assistant");
      if (assistant != null) {
        result = new CEO();
        ((CEO) result).assistant = context.deserialize(assistant, Employee.class);
      }

      // only managers have minions
      final JsonElement minons = object.get("minions");
      if (minons != null) {
        if (result == null) {
          result = new Manager();
        }
        ((Manager) result).minions = context.deserialize(minons, Employee[].class);
      }

      if (result == null) {
        result = new Employee();
      }
      result.userid = context.deserialize(object.get("userid"), String.class);
      result.startDate = context.<Long>deserialize(object.get("startDate"), long.class);
      return result;
    }
  }

  static class Employee {
    String userid;
    long startDate;

    Employee(final String userid, final long startDate) {
      this.userid = userid;
      this.startDate = startDate;
    }

    Employee() {}
  }

  static class Manager extends Employee {
    Employee[] minions;
  }

  static class CEO extends Manager {
    Employee assistant;
  }

  public static class Company {
    CEO ceo;
  }
}
