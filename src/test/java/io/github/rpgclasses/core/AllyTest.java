package io.github.rpgclasses.core;

import io.github.rpgclasses.core.ally.AllyBook;
import io.github.rpgclasses.core.ally.AllyBook.Result;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Sistema de aliados: lista unilateral por jugador. */
public class AllyTest {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3);

    @Test
    public void unilateralByDefault() {
        AllyBook book = new AllyBook();
        assertTrue("uno mismo siempre es aliado", book.considers(A, A));
        assertFalse("sin marcar = enemigo", book.considers(A, B));
        assertEquals(Result.OK, book.add(A, B, 10));
        assertTrue(book.considers(A, B));
        assertFalse("la relación no es recíproca", book.considers(B, A));
        assertFalse(book.mutual(A, B));
        assertEquals(Result.OK, book.add(B, A, 10));
        assertTrue(book.mutual(A, B));
    }

    @Test
    public void addRemoveAndLimits() {
        AllyBook book = new AllyBook();
        assertEquals(Result.SELF, book.add(A, A, 10));
        assertEquals(Result.OK, book.add(A, B, 1));
        assertEquals(Result.ALREADY, book.add(A, B, 1));
        assertEquals(Result.FULL, book.add(A, C, 1));
        assertEquals(Result.NOT_ALLY, book.remove(A, C));
        assertEquals(Result.OK, book.remove(A, B));
        assertFalse(book.considers(A, B));
        assertEquals(List.of(), book.alliesOf(A));
    }

    @Test
    public void listsAndClear() {
        AllyBook book = new AllyBook();
        book.add(A, B, 10);
        book.add(A, C, 10);
        book.add(C, B, 10);
        assertEquals(List.of(B, C), book.alliesOf(A));
        List<UUID> by = book.alliedBy(B);
        assertEquals(2, by.size());
        assertTrue(by.contains(A) && by.contains(C));
        assertEquals(2, book.clear(A));
        assertFalse(book.considers(A, B));
        assertEquals(List.of(C), book.alliedBy(B));
        assertFalse(book.considers(null, A));
    }
}
