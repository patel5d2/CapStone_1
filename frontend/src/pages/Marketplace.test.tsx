import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import type { Listing } from '../types'
import Marketplace from './Marketplace'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
  push: vi.fn(),
}))

vi.mock('../lib/api', () => ({
  api: {
    get: mocks.get,
    post: mocks.post,
    put: mocks.put,
    del: mocks.del,
  },
  toQueryString: () => '',
}))

vi.mock('../components/ui/Toast', () => ({
  useToast: () => ({ push: mocks.push }),
}))

const listing: Listing = {
  id: 42,
  sellerEmail: 'seller@example.test',
  title: 'Desk lamp',
  description: 'Works well',
  category: 'OTHER',
  listingType: 'SELL',
  status: 'AVAILABLE',
  price: 12,
  courseCode: null,
  schoolId: null,
  photoUrls: [],
  favorited: false,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

function renderMarketplace() {
  return render(
    <MemoryRouter>
      <Marketplace />
    </MemoryRouter>,
  )
}

function fileInput() {
  const input = document.querySelector<HTMLInputElement>('input[type="file"]')
  if (!input) throw new Error('Listing photo input was not rendered')
  return input
}

async function openEditForm() {
  fireEvent.click(screen.getByRole('button', { name: 'My listings' }))
  fireEvent.click(await screen.findByRole('button', { name: 'Edit' }))
  return screen.findByRole('dialog', { name: 'Edit listing' })
}

describe('Marketplace listing photos', () => {
  beforeEach(() => {
    mocks.get.mockImplementation((path: string) =>
      Promise.resolve(path === '/api/schools' ? [] : []),
    )
    mocks.post.mockResolvedValue(undefined)
    mocks.put.mockResolvedValue(listing)
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn((file: File) => `blob:${file.name}`),
    })
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('displays previews for selected images', async () => {
    renderMarketplace()
    fireEvent.click(screen.getAllByRole('button', { name: 'New listing' })[0])

    fireEvent.change(fileInput(), {
      target: {
        files: Array.from(
          { length: 4 },
          (_, index) => new File([String(index)], `photo-${index + 1}.png`, { type: 'image/png' }),
        ),
      },
    })

    expect((await screen.findByRole('img', { name: 'Selected photo 1' })).getAttribute('src'))
      .toBe('blob:photo-1.png')
    for (let index = 2; index <= 4; index++) {
      expect(screen.getByRole('img', { name: `Selected photo ${index}` }).getAttribute('src'))
        .toBe(`blob:photo-${index}.png`)
    }
  })

  it('rejects a selection of more than four images', async () => {
    renderMarketplace()
    fireEvent.click(screen.getAllByRole('button', { name: 'New listing' })[0])

    fireEvent.change(fileInput(), {
      target: {
        files: Array.from(
          { length: 5 },
          (_, index) => new File([String(index)], `${index}.png`, { type: 'image/png' }),
        ),
      },
    })

    expect(mocks.push).toHaveBeenCalledWith('You can select up to 4 images', 'error')
    expect(fileInput().value).toBe('')
    expect(screen.queryByRole('img', { name: 'Selected photo 1' })).toBeNull()
  })

  it('uploads selected files and includes returned URLs in the creation request', async () => {
    const files = [
      new File(['first'], 'first.png', { type: 'image/png' }),
      new File(['second'], 'second.png', { type: 'image/png' }),
    ]
    const uploadedUrls = [
      'https://images.example.test/first.webp',
      'https://images.example.test/second.webp',
    ]
    mocks.post.mockImplementation((path: string) =>
      Promise.resolve(path === '/api/marketplace/listing-images' ? uploadedUrls : listing),
    )

    renderMarketplace()
    fireEvent.click(screen.getAllByRole('button', { name: 'New listing' })[0])
    fireEvent.change(screen.getByPlaceholderText('Title'), {
      target: { value: 'Desk lamp' },
    })
    fireEvent.change(fileInput(), { target: { files } })
    fireEvent.click(screen.getByRole('button', { name: 'Post listing' }))

    await waitFor(() => {
      expect(mocks.post).toHaveBeenCalledWith(
        '/api/marketplace/listings',
        expect.objectContaining({ photoUrls: uploadedUrls }),
      )
    })
    const uploadCall = mocks.post.mock.calls.find(
      ([path]) => path === '/api/marketplace/listing-images',
    )
    expect(uploadCall).toBeDefined()
    if (!uploadCall) throw new Error('Photo upload request was not made')
    expect(uploadCall[1]).toBeInstanceOf(FormData)
    expect((uploadCall[1] as FormData).getAll('files')).toEqual(files)
  })

  it('retains existing URLs when editing without selecting more images', async () => {
    const existingUrls = [
      'https://images.example.test/existing-1.webp',
      'https://images.example.test/existing-2.webp',
    ]
    const ownedListing = { ...listing, photoUrls: existingUrls }
    mocks.get.mockImplementation((path: string) => {
      if (path === '/api/schools') return Promise.resolve([])
      if (path === '/api/marketplace/my-listings') return Promise.resolve([ownedListing])
      if (path === '/api/marketplace/listings/42') return Promise.resolve(ownedListing)
      return Promise.resolve([])
    })

    renderMarketplace()
    await openEditForm()
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() => {
      expect(mocks.put).toHaveBeenCalledWith(
        '/api/marketplace/listings/42',
        expect.objectContaining({ photoUrls: existingUrls }),
      )
    })
    expect(mocks.post).not.toHaveBeenCalledWith(
      '/api/marketplace/listing-images',
      expect.anything(),
    )
  })

  it('appends newly uploaded URLs when editing a listing', async () => {
    const existingUrls = ['https://images.example.test/existing.webp']
    const uploadedUrls = ['https://images.example.test/new.webp']
    const ownedListing = { ...listing, photoUrls: existingUrls }
    mocks.get.mockImplementation((path: string) => {
      if (path === '/api/schools') return Promise.resolve([])
      if (path === '/api/marketplace/my-listings') return Promise.resolve([ownedListing])
      if (path === '/api/marketplace/listings/42') return Promise.resolve(ownedListing)
      return Promise.resolve([])
    })
    mocks.post.mockResolvedValue(uploadedUrls)

    renderMarketplace()
    await openEditForm()
    fireEvent.change(fileInput(), {
      target: { files: [new File(['new'], 'new.png', { type: 'image/png' })] },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() => {
      expect(mocks.put).toHaveBeenCalledWith(
        '/api/marketplace/listings/42',
        expect.objectContaining({ photoUrls: [...existingUrls, ...uploadedUrls] }),
      )
    })
    expect(mocks.post).toHaveBeenCalledWith(
      '/api/marketplace/listing-images',
      expect.any(FormData),
    )
  })

  it('blocks an edit that would exceed four total existing and new images', async () => {
    const existingUrls = Array.from(
      { length: 4 },
      (_, index) => `https://images.example.test/existing-${index + 1}.webp`,
    )
    const ownedListing = { ...listing, photoUrls: existingUrls }
    mocks.get.mockImplementation((path: string) => {
      if (path === '/api/schools') return Promise.resolve([])
      if (path === '/api/marketplace/my-listings') return Promise.resolve([ownedListing])
      if (path === '/api/marketplace/listings/42') return Promise.resolve(ownedListing)
      return Promise.resolve([])
    })

    renderMarketplace()
    await openEditForm()
    fireEvent.change(fileInput(), {
      target: { files: [new File(['new'], 'new.png', { type: 'image/png' })] },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(mocks.push).toHaveBeenCalledWith('A listing can have at most 4 photos', 'error')
    expect(mocks.post).not.toHaveBeenCalled()
    expect(mocks.put).not.toHaveBeenCalled()
  })

  it('renders saved listing images on the listing card', async () => {
    const photoUrls = [
      'https://images.example.test/desk-lamp.webp',
      'https://images.example.test/desk-lamp-side.webp',
    ]
    mocks.get.mockImplementation((path: string) =>
      Promise.resolve(
        path === '/api/schools'
          ? []
          : path === '/api/marketplace/listings'
            ? [{ ...listing, photoUrls }]
            : [],
      ),
    )

    renderMarketplace()

    expect((await screen.findByRole('img', { name: 'Desk lamp photo 1' })).getAttribute('src'))
      .toBe(photoUrls[0])
    expect(screen.getByRole('img', { name: 'Desk lamp photo 2' }).getAttribute('src'))
      .toBe(photoUrls[1])
  })
})
