/*
 * Copyright sablintolya@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.ma1uta.mjjb.model;

import java.util.Objects;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.Size;

/**
 * Application Server Users which are registered on the Homeserver.
 */
@Entity
@Table(name = "app_user")
public class AppServerUser {

    @Id
    @Column(name = "localpart", length = 400, unique = true)
    @Size(max = 400)
    private String localpart;

    public String getLocalpart() {
        return localpart;
    }

    public void setLocalpart(String localpart) {
        this.localpart = localpart;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AppServerUser)) {
            return false;
        }
        AppServerUser that = (AppServerUser) o;
        return Objects.equals(localpart, that.localpart);
    }

    @Override
    public int hashCode() {
        return Objects.hash(localpart);
    }
}
