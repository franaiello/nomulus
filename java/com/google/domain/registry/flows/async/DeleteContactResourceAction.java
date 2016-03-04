// Copyright 2016 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.domain.registry.flows.async;

import static com.google.domain.registry.flows.ResourceFlowUtils.handlePendingTransferOnDelete;

import com.google.domain.registry.model.contact.ContactResource;
import com.google.domain.registry.model.domain.DomainBase;
import com.google.domain.registry.model.domain.ReferenceUnion;
import com.google.domain.registry.model.reporting.HistoryEntry;
import com.google.domain.registry.model.reporting.HistoryEntry.Type;
import com.google.domain.registry.request.Action;

import org.joda.time.DateTime;

import javax.inject.Inject;

/**
 * A mapreduce to delete the specified ContactResource, but ONLY if it is not referred to by any
 * existing DomainBase entity.
 */
@Action(path = "/_dr/task/deleteContactResource")
public class DeleteContactResourceAction extends DeleteEppResourceAction<ContactResource> {

  @Inject
  public DeleteContactResourceAction() {
    super(
        new DeleteContactResourceMapper(),
        new DeleteContactResourceReducer());
  }

  /** An async deletion mapper for {@link ContactResource}. */
  public static class DeleteContactResourceMapper extends DeleteEppResourceMapper<ContactResource> {

    private static final long serialVersionUID = -5904009575877950342L;

    @Override
    protected boolean isLinked(
        DomainBase domain, ReferenceUnion<ContactResource> targetResourceRef) {
      return domain.getReferencedContacts().contains(targetResourceRef);
    }
  }

  /** An async deletion reducer for {@link ContactResource}. */
  public static class DeleteContactResourceReducer
      extends DeleteEppResourceReducer<ContactResource> {

    private static final long serialVersionUID = -7633644054441045215L;

    @Override
    protected Type getHistoryType(boolean successfulDelete) {
      return successfulDelete
          ? HistoryEntry.Type.CONTACT_DELETE
          : HistoryEntry.Type.CONTACT_DELETE_FAILURE;
    }

    @Override
    protected void performDeleteTasks(
        ContactResource targetResource,
        ContactResource deletedResource,
        DateTime deletionTime,
        HistoryEntry historyEntryForDelete) {
      handlePendingTransferOnDelete(
          targetResource,
          deletedResource,
          deletionTime,
          historyEntryForDelete);
    }
  }
}