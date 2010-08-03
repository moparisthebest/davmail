/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2010  Mickael Guessant
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package davmail.exchange.ews;

import davmail.exchange.AbstractExchangeSessionTestCase;

import java.io.IOException;
import java.util.List;

/**
 * Webdav specific unit tests
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr"})
public class TestEwsExchangeSession extends AbstractExchangeSessionTestCase {
    EwsExchangeSession ewsSession;

    public void setUp() throws IOException {
        super.setUp();
        ewsSession = ((EwsExchangeSession) session);
    }

    public void testResolveNames() throws IOException {
        ResolveNamesMethod resolveNamesMethod = new ResolveNamesMethod("smtp:g");
        ewsSession.executeMethod(resolveNamesMethod);
        List<EWSMethod.Item> items = resolveNamesMethod.getResponseItems();
        for (EWSMethod.Item item:items) {
            System.out.println(item);
        }
    }
}
