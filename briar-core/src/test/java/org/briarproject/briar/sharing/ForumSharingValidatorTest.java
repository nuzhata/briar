package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.BdfMessageContext;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.briar.api.forum.Forum;
import org.jmock.Expectations;
import org.junit.Test;

import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.briarproject.briar.api.forum.ForumConstants.FORUM_SALT_LENGTH;
import static org.briarproject.briar.api.forum.ForumConstants.MAX_FORUM_NAME_LENGTH;
import static org.briarproject.briar.api.sharing.SharingConstants.MAX_INVITATION_TEXT_LENGTH;
import static org.briarproject.briar.sharing.MessageType.INVITE;

public class ForumSharingValidatorTest extends SharingValidatorTest {

	private final String forumName = getRandomString(MAX_FORUM_NAME_LENGTH);
	private final byte[] salt = getRandomBytes(FORUM_SALT_LENGTH);
	private final Forum forum = new Forum(group, forumName, salt);
	private final BdfList descriptor = BdfList.of(forumName, salt);
	private final String text = getRandomString(MAX_INVITATION_TEXT_LENGTH);

	@Override
	SharingValidator getValidator() {
		return new ForumSharingValidator(messageEncoder, clientHelper,
				metadataEncoder, clock, forumFactory);
	}

	@Override
	BdfList getDescriptor() {
		return descriptor;
	}

	@Test
	public void testAcceptsInvitationWithText() throws Exception {
		expectCreateForum(forumName);
		expectEncodeInviteMetadata(descriptor);
		BdfMessageContext context = validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor, text));
		assertExpectedContext(context, previousMsgId);
	}

	@Test
	public void testAcceptsInvitationWithNullText() throws Exception {
		expectCreateForum(forumName);
		expectEncodeInviteMetadata(descriptor);
		BdfMessageContext context = validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor, null));
		assertExpectedContext(context, previousMsgId);
	}

	@Test
	public void testAcceptsInvitationWithNullPreviousMsgId() throws Exception {
		expectCreateForum(forumName);
		expectEncodeInviteMetadata(descriptor);
		BdfMessageContext context = validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), null, descriptor, null));
		assertExpectedContext(context, null);
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullForumName() throws Exception {
		BdfList invalidDescriptor = BdfList.of(null, salt);
		validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, invalidDescriptor,
						null));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonStringForumName() throws Exception {
		BdfList invalidDescriptor = BdfList.of(123, salt);
		validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, invalidDescriptor,
						null));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortForumName() throws Exception {
		BdfList invalidDescriptor = BdfList.of("", salt);
		validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, invalidDescriptor,
						null));
	}

	@Test
	public void testAcceptsMinLengthForumName() throws Exception {
		String shortForumName = getRandomString(1);
		BdfList validDescriptor = BdfList.of(shortForumName, salt);
		expectCreateForum(shortForumName);
		expectEncodeInviteMetadata(validDescriptor);
		BdfMessageContext context = validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, validDescriptor,
						null));
		assertExpectedContext(context, previousMsgId);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongForumName() throws Exception {
		String invalidForumName = getRandomString(MAX_FORUM_NAME_LENGTH + 1);
		BdfList invalidDescriptor = BdfList.of(invalidForumName, salt);
		validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, invalidDescriptor,
						null));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullSalt() throws Exception {
		BdfList invalidDescriptor = BdfList.of(forumName, null);
		validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, invalidDescriptor,
						null));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonRawSalt() throws Exception {
		BdfList invalidDescriptor = BdfList.of(forumName, 123);
		validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, invalidDescriptor,
						null));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortSalt() throws Exception {
		byte[] invalidSalt = getRandomBytes(FORUM_SALT_LENGTH - 1);
		BdfList invalidDescriptor = BdfList.of(forumName, invalidSalt);
		validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, invalidDescriptor,
						null));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongSalt() throws Exception {
		byte[] invalidSalt = getRandomBytes(FORUM_SALT_LENGTH + 1);
		BdfList invalidDescriptor = BdfList.of(forumName, invalidSalt);
		validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, invalidDescriptor,
						null));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonStringText() throws Exception {
		expectCreateForum(forumName);
		validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor, 123));
	}

	@Test
	public void testAcceptsMinLengthText() throws Exception {
		expectCreateForum(forumName);
		expectEncodeInviteMetadata(descriptor);
		BdfMessageContext context = validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor, "1"));
		assertExpectedContext(context, previousMsgId);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongText() throws Exception {
		String invalidText = getRandomString(MAX_INVITATION_TEXT_LENGTH + 1);
		expectCreateForum(forumName);
		validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor,
						invalidText));
	}

	private void expectCreateForum(String name) {
		context.checking(new Expectations() {{
			oneOf(forumFactory).createForum(name, salt);
			will(returnValue(forum));
		}});
	}

}
