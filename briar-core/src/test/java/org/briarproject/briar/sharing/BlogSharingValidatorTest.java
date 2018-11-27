package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.BdfMessageContext;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.briar.api.blog.Blog;
import org.jmock.Expectations;
import org.junit.Test;

import static org.briarproject.bramble.test.TestUtils.getAuthor;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.briarproject.briar.api.sharing.SharingConstants.MAX_INVITATION_TEXT_LENGTH;
import static org.briarproject.briar.sharing.MessageType.INVITE;

public class BlogSharingValidatorTest extends SharingValidatorTest {

	private final Author author = getAuthor();
	private final Blog blog = new Blog(group, author, false);
	private final BdfList authorList = BdfList.of(author.getFormatVersion(),
			author.getName(), author.getPublicKey());
	private final BdfList descriptor = BdfList.of(authorList, false);
	private final String text = getRandomString(MAX_INVITATION_TEXT_LENGTH);

	@Override
	SharingValidator getValidator() {
		return new BlogSharingValidator(messageEncoder, clientHelper,
				metadataEncoder, clock, blogFactory);
	}

	@Override
	BdfList getDescriptor() {
		return descriptor;
	}

	@Test
	public void testAcceptsInvitationWithText() throws Exception {
		expectCreateBlog();
		expectEncodeInviteMetadata(descriptor);
		BdfMessageContext context = validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor, text));
		assertExpectedContext(context, previousMsgId);
	}

	@Test
	public void testAcceptsInvitationWithNullText() throws Exception {
		expectCreateBlog();
		expectEncodeInviteMetadata(descriptor);
		BdfMessageContext context = validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor, null));
		assertExpectedContext(context, previousMsgId);
	}

	@Test
	public void testAcceptsInvitationWithNullPreviousMsgId() throws Exception {
		expectCreateBlog();
		expectEncodeInviteMetadata(descriptor);
		BdfMessageContext context = validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), null, descriptor, text));
		assertExpectedContext(context, null);
	}

	@Test
	public void testAcceptsInvitationForRssBlog() throws Exception {
		BdfList rssDescriptor = BdfList.of(authorList, true);
		expectCreateRssBlog();
		expectEncodeInviteMetadata(rssDescriptor);
		BdfMessageContext context = validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, rssDescriptor,
						text));
		assertExpectedContext(context, previousMsgId);
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullAuthor() throws Exception {
		BdfList invalidDescriptor = BdfList.of(null, false);
		validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, invalidDescriptor,
						null));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonListAuthor() throws Exception {
		BdfList invalidDescriptor = BdfList.of(123, false);
		validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, invalidDescriptor,
						null));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonStringText() throws Exception {
		expectCreateBlog();
		validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor, 123));
	}

	@Test
	public void testAcceptsMinLengthText() throws Exception {
		String shortText = getRandomString(1);
		expectCreateBlog();
		expectEncodeInviteMetadata(descriptor);
		BdfMessageContext context = validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor,
						shortText));
		assertExpectedContext(context, previousMsgId);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongText() throws Exception {
		String invalidText = getRandomString(MAX_INVITATION_TEXT_LENGTH + 1);
		expectCreateBlog();
		validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor,
						invalidText));
	}

	private void expectCreateBlog() throws Exception {
		context.checking(new Expectations() {{
			oneOf(clientHelper).parseAndValidateAuthor(authorList);
			will(returnValue(author));
			oneOf(blogFactory).createBlog(author);
			will(returnValue(blog));
		}});
	}

	private void expectCreateRssBlog() throws Exception {
		context.checking(new Expectations() {{
			oneOf(clientHelper).parseAndValidateAuthor(authorList);
			will(returnValue(author));
			oneOf(blogFactory).createFeedBlog(author);
			will(returnValue(blog));
		}});
	}
}
